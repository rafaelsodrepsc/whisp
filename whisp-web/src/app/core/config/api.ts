import { isDevMode } from '@angular/core';

function websocketUrl(): string {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${scheme}://${window.location.host}/ws/websocket`;
}

// em desenvolvimento (ng serve) cada serviço roda em uma porta própria;
// em produção o nginx expõe tudo na mesma origem do site
export const apiConfig = isDevMode()
  ? {
      auth: 'http://localhost:8081/auth',
      rooms: 'http://localhost:8082/rooms',
      messages: 'http://localhost:8083/rooms',
      websocket: () => 'ws://localhost:8082/ws/websocket',
    }
  : {
      auth: '/auth',
      rooms: '/rooms',
      messages: '/rooms',
      websocket: websocketUrl,
    };
