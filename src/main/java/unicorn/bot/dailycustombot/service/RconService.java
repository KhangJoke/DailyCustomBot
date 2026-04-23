package unicorn.bot.dailycustombot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.EnvLoader;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Service kết nối RCON đến Minecraft Server để gửi lệnh whitelist.
 *
 * Sử dụng pure Java Socket — không cần thư viện bên ngoài.
 * Triển khai giao thức Source RCON (dùng chung cho Minecraft):
 * - Packet format: [Length 4B LE] [RequestID 4B LE] [Type 4B LE] [Payload
 * null-terminated] [Padding 1B null]
 * - Type 3 = Login (SERVERDATA_AUTH)
 * - Type 2 = Command (SERVERDATA_EXECCOMMAND) / Auth Response
 * (SERVERDATA_AUTH_RESPONSE)
 *
 * Cấu hình qua biến môi trường: RCON_IP, RCON_PORT, RCON_PASS.
 */
public class RconService {

    private static final Logger logger = LoggerFactory.getLogger(RconService.class);

    /** Timeout kết nối và đọc socket (ms) */
    private static final int SOCKET_TIMEOUT_MS = 5000;

    // Các loại packet trong giao thức RCON
    private static final int SERVERDATA_AUTH = 3;
    private static final int SERVERDATA_EXECCOMMAND = 2;

    /**
     * Gửi lệnh whitelist add cho nhân vật Minecraft.
     *
     * @param minecraftUsername Tên nhân vật Minecraft cần thêm vào whitelist
     * @return Phản hồi từ Minecraft Server
     * @throws RconException Khi không thể kết nối, xác thực thất bại, hoặc lỗi I/O
     */
    public String addWhitelist(String minecraftUsername) throws RconException {
        String command = "easywhitelist add " + minecraftUsername;
        return sendCommand(command);
    }

    /**
     * Gửi lệnh whitelist remove cho nhân vật Minecraft.
     *
     * @param minecraftUsername Tên nhân vật Minecraft cần xóa khỏi whitelist
     * @return Phản hồi từ Minecraft Server
     * @throws RconException Khi không thể kết nối, xác thực thất bại, hoặc lỗi I/O
     */
    public String removeWhitelist(String minecraftUsername) throws RconException {
        String command = "easywhitelist remove " + minecraftUsername;
        return sendCommand(command);
    }

    /**
     * Kết nối RCON, xác thực mật khẩu, gửi lệnh và trả về kết quả.
     *
     * @param command Lệnh Minecraft cần thực thi
     * @return Phản hồi text từ server
     * @throws RconException Khi có lỗi ở bất kỳ bước nào
     */
    public String sendCommand(String command) throws RconException {
        String rconIp = EnvLoader.get("RCON_IP");
        String rconPortStr = EnvLoader.get("RCON_PORT");
        String rconPass = EnvLoader.get("RCON_PASS");

        // Kiểm tra cấu hình bắt buộc
        if (rconIp == null || rconIp.isBlank()) {
            throw new RconException("Biến môi trường RCON_IP chưa được cấu hình!");
        }
        if (rconPortStr == null || rconPortStr.isBlank()) {
            throw new RconException("Biến môi trường RCON_PORT chưa được cấu hình!");
        }
        if (rconPass == null || rconPass.isBlank()) {
            throw new RconException("Biến môi trường RCON_PASS chưa được cấu hình!");
        }

        int rconPort;
        try {
            rconPort = Integer.parseInt(rconPortStr);
        } catch (NumberFormatException e) {
            throw new RconException("RCON_PORT không phải số hợp lệ: " + rconPortStr);
        }

        logger.info("Kết nối RCON đến {}:{} — Lệnh: {}", rconIp, rconPort, command);

        try (Socket socket = new Socket()) {
            // Kết nối với timeout
            socket.connect(new InetSocketAddress(rconIp, rconPort), SOCKET_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            // Bước 1: Xác thực (Login) với mật khẩu RCON
            sendPacket(out, 1, SERVERDATA_AUTH, rconPass);
            RconPacket authResponse = readPacket(in);

            if (authResponse.requestId() == -1) {
                throw new RconException("Xác thực RCON thất bại — sai mật khẩu RCON!");
            }
            logger.info("Xác thực RCON thành công.");

            // Bước 2: Gửi lệnh Minecraft
            sendPacket(out, 2, SERVERDATA_EXECCOMMAND, command);
            RconPacket cmdResponse = readPacket(in);

            String responseText = cmdResponse.payload();
            logger.info("RCON phản hồi: {}", responseText);
            return responseText;

        } catch (java.net.ConnectException e) {
            throw new RconException("Không thể kết nối đến Minecraft Server RCON tại "
                    + rconIp + ":" + rconPort + " — Server có thể đang tắt.", e);
        } catch (java.net.SocketTimeoutException e) {
            throw new RconException("Kết nối RCON bị timeout sau " + SOCKET_TIMEOUT_MS + "ms.", e);
        } catch (IOException e) {
            throw new RconException("Lỗi I/O khi giao tiếp RCON: " + e.getMessage(), e);
        }
    }

    /**
     * Gửi một packet RCON qua output stream.
     * Format: [PacketSize 4B LE] [RequestID 4B LE] [Type 4B LE] [Payload + \0] [\0]
     */
    private void sendPacket(DataOutputStream out, int requestId, int type, String payload) throws IOException {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        // Kích thước packet = RequestID(4) + Type(4) + Payload(n) + NullTerminator(1) +
        // Padding(1)
        int packetSize = 4 + 4 + payloadBytes.length + 1 + 1;

        ByteBuffer buffer = ByteBuffer.allocate(4 + packetSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(packetSize); // Kích thước payload (không bao gồm chính nó)
        buffer.putInt(requestId); // ID để ghép cặp request-response
        buffer.putInt(type); // Loại packet (AUTH hoặc EXECCOMMAND)
        buffer.put(payloadBytes); // Nội dung lệnh / mật khẩu
        buffer.put((byte) 0); // Null terminator cho payload
        buffer.put((byte) 0); // Padding byte theo spec

        out.write(buffer.array());
        out.flush();
    }

    /**
     * Đọc một packet RCON từ input stream.
     * Trả về RconPacket chứa requestId, type, và payload text.
     */
    private RconPacket readPacket(DataInputStream in) throws IOException {
        // Đọc kích thước packet (4 bytes, Little Endian)
        byte[] sizeBytes = new byte[4];
        in.readFully(sizeBytes);
        int packetSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // Đọc toàn bộ nội dung packet
        byte[] packetData = new byte[packetSize];
        in.readFully(packetData);

        ByteBuffer packetBuffer = ByteBuffer.wrap(packetData).order(ByteOrder.LITTLE_ENDIAN);
        int requestId = packetBuffer.getInt();
        int type = packetBuffer.getInt();

        // Payload = phần còn lại trừ 2 byte null (terminator + padding)
        int payloadLength = packetSize - 4 - 4 - 2;
        byte[] payloadBytes = new byte[Math.max(0, payloadLength)];
        packetBuffer.get(payloadBytes);

        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        return new RconPacket(requestId, type, payload);
    }

    /**
     * Cấu trúc dữ liệu đại diện cho một packet RCON đã được parse.
     */
    private record RconPacket(int requestId, int type, String payload) {
    }

    /**
     * Exception chuyên biệt cho các lỗi liên quan đến RCON.
     * Giúp phân biệt lỗi RCON với các loại exception khác.
     */
    public static class RconException extends Exception {
        public RconException(String message) {
            super(message);
        }

        public RconException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
