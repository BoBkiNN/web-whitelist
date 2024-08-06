import json
import traceback as tb
from threading import Thread

import flask_sock as ws
from flask import Flask

import wlproto

app = Flask(__name__)
sock = ws.Sock(app)


@sock.route('/whitelist/<server>')
def ws_route(conn: ws.Server, server: str):
    c = WsConnection(server, conn)
    servers[server] = c
    c.run()


class WsConnection(Thread):
    def __init__(self, server: str, conn: ws.Server) -> None:
        self.server = server
        self.conn = conn
        self.working = True
        super().__init__(name=f"WsConnection({server})")

    def on_connected(self):
        print(f"Connected from {self.server}")

    def on_message(self, data: str | bytes):
        if isinstance(data, bytes):
            data = bytes.decode(data)
        try:
            j = json.loads(data)
            msg = wlproto.load(j)
            print(f"Got message `{msg.meta.msg_type}`:", msg)
        except Exception as e:
            print("Error parsing:", e)
            tb.print_exception(e)

    def on_disconnect(self):
        print(f"Disconnected {self.server}")
        servers.pop(self.server)

    def run(self):
        self.on_connected()
        while self.working:
            try:
                data: str = self.conn.receive()  # type: ignore
            except ws.ConnectionClosed as e:
                print(e)
                break
            self.on_message(data)
        self.on_disconnect()

    def send_msg(self, msg: wlproto.Message):
        d = wlproto.write(msg)
        j = json.dumps(d, ensure_ascii=False)
        print("Sending", j)
        self.conn.send(j)


servers: dict[str, WsConnection] = {}


@app.route("/info")
def do_info():
    msg = wlproto.Info.new()
    s = servers.get("main", None)
    if s is None:
        return "Server not connected"
    s.send_msg(msg)
    return f"sent {msg}"


@app.route("/aboba")
def do_error():
    class Aboba(wlproto.Message):
        def __init__(self, meta: wlproto.MessageMeta):
            super().__init__(meta)

    msg = Aboba(wlproto.MessageMeta.of("aboba"))
    s = servers.get("main", None)
    if s is None:
        return "Server not connected"
    s.send_msg(msg)
    return f"sent {msg}"


@app.route("/list")
def do_list():
    msg = wlproto.List.new()
    s = servers.get("main", None)
    if s is None:
        return "Server not connected"
    s.send_msg(msg)
    return f"sent {msg}"


@app.route("/add/<player>")
def do_add(player: str):
    msg = wlproto.Add.new(players=[player])
    s = servers.get("main", None)
    if s is None:
        return "Server not connected"
    s.send_msg(msg)
    return f"sent {msg}"


@app.route("/remove/<player>")
def do_remove(player: str):
    msg = wlproto.Remove.new(players=[player])
    s = servers.get("main", None)
    if s is None:
        return "Server not connected"
    s.send_msg(msg)
    return f"sent {msg}"


if __name__ == '__main__':
    app.run(host='127.0.0.1', port=8080, debug=True)
