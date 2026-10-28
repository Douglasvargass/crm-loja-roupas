import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

const control =
  'w-full rounded-lg border bg-surface px-3 text-sm text-text placeholder:text-muted/70 ' +
  'focus:border-primary focus:ring-2 focus:ring-primary/25 focus:outline-none disabled:opacity-60';

interface WrapperProps {
  label: string;
  error?: string;
  hint?: string;
  className?: string;
  children: (id: string, describedBy: string | undefined) => ReactNode;
}

// Erro de validação aparece ao lado do campo (HU31).
function FieldWrapper({ label, error, hint, className, children }: WrapperProps) {
  const id = useId();
  const msgId = `${id}-msg`;
  return (
    <div className={cn('flex flex-col gap-1', className)}>
      <label htmlFor={id} className="text-sm font-medium text-text">
        {label}
      </label>
      {children(id, error || hint ? msgId : undefined)}
      {error ? (
        <p id={msgId} className="text-xs text-error">
          {error}
        </p>
      ) : hint ? (
        <p id={msgId} className="text-xs text-muted">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

type InputProps = InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string };

export function Input({ label, error, hint, className, ...rest }: InputProps) {
  return (
    <FieldWrapper label={label} error={error} hint={hint} className={className}>
      {(id, describedBy) => (
        <input
          id={id}
          aria-invalid={!!error || undefined}
          aria-describedby={describedBy}
          {...rest}
          className={cn(control, 'h-10', error ? 'border-error' : 'border-border')}
        />
      )}
    </FieldWrapper>
  );
}

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & { label: string; error?: string };

export function Select({ label, error, className, children, ...rest }: SelectProps) {
  return (
    <FieldWrapper label={label} error={error} className={className}>
      {(id, describedBy) => (
        <select
          id={id}
          aria-invalid={!!error || undefined}
          aria-describedby={describedBy}
          {...rest}
          className={cn(control, 'h-10', error ? 'border-error' : 'border-border')}
        >
          {children}
        </select>
      )}
    </FieldWrapper>
  );
}

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; error?: string };

export function Textarea({ label, error, className, ...rest }: TextareaProps) {
  return (
    <FieldWrapper label={label} error={error} className={className}>
      {(id, describedBy) => (
        <textarea
          id={id}
          rows={3}
          aria-invalid={!!error || undefined}
          aria-describedby={describedBy}
          {...rest}
          className={cn(control, 'py-2', error ? 'border-error' : 'border-border')}
        />
      )}
    </FieldWrapper>
  );
}
