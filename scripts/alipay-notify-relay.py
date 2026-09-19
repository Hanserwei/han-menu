#!/usr/bin/env python3
"""为本机沙箱验收转发唯一通知路径，不暴露应用的顾客、管理或健康接口。"""

import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import Request, urlopen

NOTIFICATION_PATH = "/api/v1/payment-notifications/alipay"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8191)
    parser.add_argument("--application-port", type=int, default=8185)
    args = parser.parse_args()
    if not all(1024 <= port <= 65535 for port in (args.port, args.application_port)):
        parser.error("端口必须在 1024 至 65535 之间")
    target = f"http://127.0.0.1:{args.application_port}{NOTIFICATION_PATH}"

    class NotificationRelay(BaseHTTPRequestHandler):
        """请求体最终由真实后端验签，日志只输出响应码。"""

        def log_message(self, *_):
            pass

        def do_GET(self):
            self.send_response(404)
            self.end_headers()

        def do_POST(self):
            if self.path != NOTIFICATION_PATH:
                self.send_response(404)
                self.end_headers()
                return
            try:
                size = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                size = 0
            if not 0 < size <= 16384:
                self.send_response(413)
                self.end_headers()
                return
            request = Request(
                target,
                data=self.rfile.read(size),
                headers={"Content-Type": "application/x-www-form-urlencoded"},
                method="POST",
            )
            try:
                with urlopen(request, timeout=15) as response:
                    status, body = response.status, response.read(1024)
            except HTTPError as error:
                status, body = error.code, error.read(1024)
            except OSError:
                status, body = 503, b"failure"
            self.send_response(status)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(body)
            print(f"alipay_callback status={status} accepted={body == b'success'}", flush=True)

    with ThreadingHTTPServer(("127.0.0.1", args.port), NotificationRelay) as server:
        server.serve_forever()


if __name__ == "__main__":
    main()
