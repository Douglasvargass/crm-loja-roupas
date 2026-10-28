import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { useSession } from '@/app/context/SessionContext';
import { useToast } from '@/app/context/ToastContext';
import { useCategories, useDeleteCategory, useSaveCategory } from '@/data/hooks';
import type { CategoryDto } from '@/data/dto';
import { can } from '@/domain/permissions';
import { Badge } from '../components/atoms/Badge';
import { Button } from '../components/atoms/Button';
import { Input, Select } from '../components/atoms/Field';
import { Icon } from '../components/atoms/Icon';
import { ConfirmDialog } from '../components/molecules/ConfirmDialog';
import { Modal } from '../components/molecules/Modal';
import { QueryState } from '../components/molecules/QueryState';
import { DataTable } from '../components/organisms/DataTable';
import { Card, PageHeader } from '../components/templates/Page';

export function CategoriesPage() {
  const { role } = useSession();
  const manage = !!role && can.manageCategories(role);
  const query = useCategories();
  const remove = useDeleteCategory();
  const toast = useToast();
  const [editing, setEditing] = useState<CategoryDto | 'new' | null>(null);
  const [deleting, setDeleting] = useState<CategoryDto | null>(null);

  async function doDelete() {
    if (!deleting) return;
    try {
      await remove.mutateAsync(deleting.id);
      toast.success('Pronto! A categoria foi removida.');
    } catch (err) {
      toast.error(err);
    } finally {
      setDeleting(null);
    }
  }

  return (
    <>
      <PageHeader
        title="Categorias"
        subtitle="Organizam os produtos (camisetas, calças, vestidos…)."
        actions={manage && <Button onClick={() => setEditing('new')}><Icon name="plus" className="size-4" />Nova categoria</Button>}
      />
      <Card>
        <QueryState query={query} isEmpty={(d) => d.length === 0} empty="Nenhuma categoria cadastrada.">
          {(rows) => (
            <DataTable<CategoryDto>
              rows={rows}
              rowKey={(c) => c.id}
              columns={[
                { header: 'Nome', cell: (c) => <span className="font-medium">{c.name}</span> },
                { header: 'Descrição', cell: (c) => <span className="text-muted">{c.description ?? '—'}</span> },
                {
                  header: 'Produtos',
                  align: 'right',
                  cell: (c) => <Link to={`/produtos?categoria=${c.id}`} className="tabular text-primary hover:underline">{c.productCount}</Link>,
                },
                { header: 'Situação', cell: (c) => <Badge tone={c.active ? 'success' : 'neutral'}>{c.active ? 'Ativa' : 'Inativa'}</Badge> },
                ...(manage
                  ? [{
                      header: 'Ações',
                      align: 'right' as const,
                      cell: (c: CategoryDto) => (
                        <div className="flex justify-end gap-1">
                          <Button size="sm" variant="ghost" onClick={() => setEditing(c)}>Editar</Button>
                          <Button size="sm" variant="ghost" onClick={() => setDeleting(c)} aria-label={`Excluir ${c.name}`}>
                            <Icon name="trash" className="size-4" />
                          </Button>
                        </div>
                      ),
                    }]
                  : []),
              ]}
            />
          )}
        </QueryState>
      </Card>

      {editing && <CategoryModal category={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
      <ConfirmDialog
        open={!!deleting}
        title="Excluir categoria"
        message={
          deleting && deleting.productCount > 0
            ? <>A categoria <strong>{deleting.name}</strong> tem {deleting.productCount} produto(s) e não pode ser excluída. Você pode inativá-la em “Editar”.</>
            : <>Excluir a categoria <strong>{deleting?.name}</strong>?</>
        }
        confirmLabel="Excluir"
        danger
        loading={remove.isPending}
        onConfirm={() => void doDelete()}
        onClose={() => setDeleting(null)}
      />
    </>
  );
}

function CategoryModal({ category, onClose }: { category?: CategoryDto; onClose: () => void }) {
  const save = useSaveCategory();
  const toast = useToast();
  const [name, setName] = useState(category?.name ?? '');
  const [description, setDescription] = useState(category?.description ?? '');
  const [active, setActive] = useState(category?.active ?? true);
  const [error, setError] = useState<string>();

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      setError('Informe o nome da categoria.');
      return;
    }
    try {
      await save.mutateAsync({ id: category?.id, input: { name: name.trim(), description: description.trim() || null, active } });
      toast.success(category ? 'Categoria atualizada.' : 'Categoria cadastrada.');
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : undefined);
    }
  }

  return (
    <Modal
      open
      title={category ? 'Editar categoria' : 'Nova categoria'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button type="submit" form="category-form" loading={save.isPending}>Salvar</Button>
        </>
      }
    >
      <form id="category-form" onSubmit={submit} noValidate className="flex flex-col gap-3">
        <Input label="Nome" value={name} error={error} autoFocus onChange={(e) => setName(e.target.value)} />
        <Input label="Descrição (opcional)" value={description} onChange={(e) => setDescription(e.target.value)} />
        <Select label="Situação" value={String(active)} onChange={(e) => setActive(e.target.value === 'true')}>
          <option value="true">Ativa</option>
          <option value="false">Inativa (não aparece em produtos novos)</option>
        </Select>
      </form>
    </Modal>
  );
}
