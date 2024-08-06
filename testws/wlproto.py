from enum import Enum
import uuid
from typing import Any, TypeVar


class Error:
    def __init__(self, full: dict):
        self.type: str = full["type"]
        self.msg: str = full["msg"]

    def __str__(self) -> str:
        return f"{self.type}({self.msg})"


def random_id():
    return str(uuid.uuid4())


class MessageState(Enum):
    NOT_SENT = 0
    SUCCESS = 1
    FAIL = 2

    def __str__(self) -> str:
        return self.name

    def __eq__(self, value: object) -> bool:
        if isinstance(value, MessageState):
            o: MessageState = value
            return o.value == self.value
        elif value:
            return self.value == MessageState.SUCCESS
        elif not value:
            return self.value == MessageState.FAIL
        elif value is None:
            return self.value == MessageState.NOT_SENT
        else:
            return False

    def is_fail(self):
        return self == MessageState.FAIL

    @staticmethod
    def from_bool(b: bool | None):
        if b is None:
            return MessageState.NOT_SENT
        elif b:
            return MessageState.SUCCESS
        elif not b:
            return MessageState.FAIL


class Message:

    @classmethod
    def new(cls):
        """Simple argument-less create implementation"""
        return cls(MessageMeta.of(cls))

    def get_type(self) -> str:
        return self.meta.msg_type

    def get_state(self) -> MessageState:
        return self.meta.state

    def get_id(self):
        return self.meta.msg_id

    def __init__(self, meta: "MessageMeta", **kwargs):
        self.meta = meta

    def __str__(self) -> str:
        return f"{type(self).__name__}({self.meta})"

    def write(self, data: dict):
        pass


def load(data: dict | None):
    if data is None:
        raise ValueError("dict is none")
    msg_type: str = data["type"]
    t = TYPES.get(msg_type, None)
    if t is None:
        raise KeyError(f"Unknown type '{msg_type}'")
    meta = MessageMeta(msg_type, full=data)
    md = data["data"]
    try:
        msg = t(meta=meta, data=md)
    except Exception as e:
        raise ValueError(f"Failed to create message {meta} from data {md}", e)
    return msg


def write(msg: Message) -> dict:
    return msg.meta.write(msg)


K = TypeVar("K")
V = TypeVar("V")


def key_by_value(value: V, d: dict[K, V]) -> K:
    k = [i for i in d if d[i] == value]
    return k[0]


class MessageMeta:
    def __init__(self, msg_type: str, full: dict[str, Any] | None = None) -> None:
        self.msg_type = msg_type
        self.errors: list[Error] = []
        if full is not None:
            self.msg_id: str = full["id"]
            self.state = MessageState.from_bool(full.get("success", True))
            if self.state is False and "errors" in full:
                for d in full["errors"]:
                    self.errors.append(Error(d))
        else:
            self.msg_id = random_id()
            self.state = MessageState.NOT_SENT

    def write(self, msg: Message):
        d = {}
        msg.write(d)
        return {
            "type": self.msg_type,
            "id": self.msg_id,
            "data": d
        }

    def __str__(self) -> str:
        return f"{self.msg_type}:{self.msg_id}:{self.state}"

    @staticmethod
    def of(t: type[Message] | str):
        if isinstance(t, str):
            return MessageMeta(t)
        if type(t) is Message:
            raise ValueError("Base class cannot be instantiated")
        r = key_by_value(t, TYPES)
        return MessageMeta(r)


class List(Message):

    def __init__(self, meta: MessageMeta, data: dict | None = None):
        super().__init__(meta)
        if data is not None:
            self.players: list[str] = data["players"]
        else:
            self.players: list[str] = []

    def __str__(self) -> str:
        return super().__str__() + "{" + str(self.players) + "}"


class Info(Message):

    def __init__(self, meta: MessageMeta, data: dict | None = None):
        super().__init__(meta)
        if data is None:
            self.version = None
            self.online: list[str] = []
            self.handlers: list[str] = []
            self.logs: list[ActionLog] = []
            return
        self.version = data["plugin_version"]
        self.online: list[str] = data["players_online"]
        self.handlers: list[str] = data["handlers"]
        self.logs: list[ActionLog] = []
        for d in data["logs"]:
            self.logs.append(ActionLog(d))

    def __str__(self) -> str:
        return super().__str__() + str({
            "version": self.version,
            "online": self.online,
            "handlers": self.handlers,
            "logs": [str(log) for log in self.logs]
        })


class Action(Enum):
    ADDED = 0
    REMOVED = 1
    CONNECTED = 2
    DISCONNECTED = 3
    FAILED_TO_CONNECT = 4


class ActionLog:
    def __init__(self, data: dict) -> None:
        self.time = data["timestamp"]
        self.action = Action[data["action"]]
        self.players: list[str] = data["players"]

    def __str__(self) -> str:
        return str({
            "time": self.time,
            "action": self.action.name,
            "players": self.players
        })


# noinspection PyMethodOverriding
class Add(Message):

    @classmethod
    def new(cls, players: list[str]):
        """Create new add message"""
        return cls(MessageMeta.of(cls), players=players)

    def __init__(self, meta: MessageMeta, data: dict | None = None, players: list[str] | None = None):
        super().__init__(meta)
        if players is not None:
            self.players = players
        elif data is not None and self.get_state().is_fail():
            self.players: list[str] = data["players"]
        elif data is not None:
            self.players = []
        else:
            raise ValueError("not a read; not a construct")

    def write(self, data: dict):
        data["players"] = self.players

    def __str__(self) -> str:
        return super().__str__() + "{" + str(self.players) + "}"


# noinspection PyMethodOverriding
class Remove(Message):

    @classmethod
    def new(cls, players: list[str]):
        """Create new remove message"""
        return cls(MessageMeta.of(cls), players=players)

    def __init__(self, meta: MessageMeta, data: dict | None = None, players: list[str] | None = None):
        super().__init__(meta)
        if players is not None:
            self.players = players
        elif data is not None and self.get_state().is_fail():
            self.players: list[str] = data["players"]
        elif data is not None:
            self.players = []
        else:
            raise ValueError("not a read; not a construct")

    def write(self, data: dict):
        data["players"] = self.players

    def __str__(self) -> str:
        return super().__str__() + "{" + str(self.players) + "}"


class Unknown(Message):
    """Used to handle messages when unknown message type is sent"""

    def __init__(self, meta: MessageMeta, data: dict):
        super().__init__(meta)


TYPES: dict[str, type[Message]] = {
    "info": Info,
    "list": List,
    "add": Add,
    "remove": Remove,
    "unknown": Unknown
}
