import { setupServer } from 'msw/node';
import { handlers } from './handlers';

// Shared MSW server for tests. Individual tests add per-case handlers with
// `server.use(...)`; unhandled requests error (see setup.ts) so nothing hits a
// real network.
export const server = setupServer(...handlers);
