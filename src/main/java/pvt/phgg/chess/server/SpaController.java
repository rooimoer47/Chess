package pvt.phgg.chess.server;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Serve index.html for all non-API, non-asset paths so React Router handles routing.
// Spring MVC precedence ensures /api/** RestControllers always win over this catch-all.
// WebSocket upgrade requests bypass the MVC dispatcher entirely so /ws/game is safe.
@Controller
public class SpaController {
    @GetMapping(value = "/**", produces = "text/html")
    public String forward() {
        return "forward:/index.html";
    }
}
