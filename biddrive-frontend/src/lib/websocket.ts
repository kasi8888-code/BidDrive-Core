import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { WS_URL } from './api';

type Handler = (payload: any) => void;

class WebSocketService {
  private client: Client | null = null;
  private subscriptions: Map<string, { id: string; handler: Handler }[]> = new Map();
  private connected = false;
  private _token: string | null = null;

  connect(token: string, onConnect?: () => void) {
    this._token = token;
    if (this.client?.active) return;

    this.client = new Client({
      webSocketFactory: () => new SockJS(WS_URL) as any,
      connectHeaders: { Authorization: `Bearer ${token}` },
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      reconnectDelay: 3000,
      onConnect: () => {
        this.connected = true;

        this.subscriptions.forEach((handlers, topic) => {
          handlers.forEach(({ handler }) => this._subscribe(topic, handler));
        });
        onConnect?.();
      },
      onDisconnect: () => {
        this.connected = false;
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame);
      },
    });
    this.client.activate();
  }

  private _subscribe(topic: string, handler: Handler) {
    if (!this.client?.active) return null;
    return this.client.subscribe(topic, (msg: IMessage) => {
      try {
        handler(JSON.parse(msg.body));
      } catch {
        handler(msg.body);
      }
    });
  }

  subscribe(topic: string, handler: Handler): () => void {
    if (!this.subscriptions.has(topic)) {
      this.subscriptions.set(topic, []);
    }

    let subId = '';
    if (this.connected && this.client?.active) {
      const sub = this._subscribe(topic, handler);
      subId = sub?.id ?? '';
    }

    const entry = { id: subId, handler };
    this.subscriptions.get(topic)!.push(entry);

    return () => {
      const list = this.subscriptions.get(topic) ?? [];
      const idx = list.indexOf(entry);
      if (idx !== -1) list.splice(idx, 1);

      if (subId && this.client?.active) {
        this.client.unsubscribe(subId);
      }
    };
  }

  disconnect() {
    this.client?.deactivate();
    this.subscriptions.clear();
    this.connected = false;
  }

  isConnected() {
    return this.connected;
  }
}

export const wsService = new WebSocketService();
