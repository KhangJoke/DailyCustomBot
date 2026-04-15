package unicorn.bot.dailycustombot.api;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller xử lý REST API cho team confirmation.
 * Route: POST /api/confirm-team
 */
public class TeamController {

    private static final Logger logger = LoggerFactory.getLogger(TeamController.class);

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    /**
     * POST /api/confirm-team
     * Body: JSON ConfirmTeamRequest
     * Response: JSON ConfirmTeamResponse
     */
    public void confirmTeam(Context ctx) {
        try {
            ConfirmTeamRequest request = ctx.bodyAsClass(ConfirmTeamRequest.class);

            logger.info("API confirm-team: team='{}', short='{}', captain='{}', members={}, row={}",
                    request.teamName(), request.shortName(), request.captainDiscord(),
                    request.membersDiscord(), request.rowNumber());

            ConfirmTeamResponse response = teamService.confirmTeam(request);

            if (response.success()) {
                ctx.status(HttpStatus.OK).json(response);
                logger.info("API confirm-team SUCCESS: {} — {}", response.action(), response.message());
            } else {
                ctx.status(HttpStatus.BAD_REQUEST).json(response);
                logger.warn("API confirm-team FAILED: {}", response.message());
            }

        } catch (Exception e) {
            logger.error("API confirm-team ERROR: {}", e.getMessage(), e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .json(ConfirmTeamResponse.error("Lỗi server: " + e.getMessage()));
        }
    }
}
