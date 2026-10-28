// Avisa o SessionContext quando a sessão cai (refresh falhou), sem o http.ts depender de React.
type Listener = () => void;
const listeners = new Set<Listener>();

export const authEvents = {
  onSessionLost(fn: Listener) {
    listeners.add(fn);
    return () => {
      listeners.delete(fn);
    };
  },
  emitSessionLost() {
    listeners.forEach((fn) => fn());
  },
};
