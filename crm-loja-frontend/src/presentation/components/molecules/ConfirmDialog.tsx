import type { ReactNode } from 'react';
import { Button } from '../atoms/Button';
import { Modal } from './Modal';

interface Props {
  open: boolean;
  title: string;
  message: ReactNode;
  confirmLabel: string;
  danger?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
  children?: ReactNode;
}

// Exclusões e cancelamentos sempre pedem confirmação (HU31).
export function ConfirmDialog({ open, title, message, confirmLabel, danger, loading, onConfirm, onClose, children }: Props) {
  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            Voltar
          </Button>
          <Button variant={danger ? 'danger' : 'primary'} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="text-sm text-text">{message}</div>
      {children}
    </Modal>
  );
}
