package pvt.phgg.chess.server;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Serve index.html for any path that doesn't have a file extension and isn't an
// API/WebSocket path. This allows React Router (BrowserRouter) to handle navigation
// client-side even when a subpath is loaded or refreshed directly.
@Controller
public class SpaController {
    @GetMapping({"/{path:[^\\.]+}", "/**/{path:[^\\.]+}"})
    public String forward() {
        return "forward:/index.html";
    }
}
