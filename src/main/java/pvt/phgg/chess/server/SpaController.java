package pvt.phgg.chess.server;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

// Serve index.html for all non-API, non-asset paths so React Router handles routing.
// Spring MVC precedence ensures /api/** RestControllers always win over this catch-all.
// WebSocket upgrade requests bypass the MVC dispatcher entirely so /ws/game is safe.
@Controller
public class SpaController {
    @GetMapping(value = "/**", produces = "text/html")
    @ResponseBody
    public Resource index() {
        return new ClassPathResource("static/index.html");
    }
}
