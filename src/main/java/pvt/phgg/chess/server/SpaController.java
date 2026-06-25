package pvt.phgg.chess.server;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Serve index.html for the React Router client-side routes so that direct navigation
// or page refresh works. List routes explicitly to avoid catching /ws/** and /api/**.
@Controller
public class SpaController {
    @GetMapping({"/game", "/history", "/history/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
