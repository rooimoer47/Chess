#!/bin/bash
IP=$(curl -s ifconfig.me)
URL="http://${IP}:8080"
echo "$URL"
xdg-open "$URL" 2>/dev/null || open "$URL" 2>/dev/null || echo "Could not open browser automatically"
