# 01-acesso · Acesso: login, troca de senha, papéis e auditoria

## Contexto

Parte do MVP de CRM da loja de roupas (enunciado "Mini Projeto SENAI – Loja"). Histórias e regras completas em docs/03-historias-de-usuario.md e docs/04-regras-de-negocio.md.

## Requisitos

### REQ-01 · HU33 · Entrar no sistema

Como usuário, quero entrar com login e senha, para usar só o que meu papel permite.

- Login e senha corretos abrem a tela inicial do papel (Gerente: Dashboard; Vendedor: Vendas; Estoquista: Estoque).
- Login ou senha errados, conta inativa ou bloqueada mostram a mesma frase: "Login ou senha incorretos".
- A sessão dura enquanto a pessoa usa o sistema; parada por mais de 24 h, pede login de novo.
- Botão Sair encerra a sessão neste navegador.

### REQ-02 · HU34 · Trocar a senha

Como usuário, quero trocar minha senha, para manter a conta segura.

- No primeiro acesso a troca é obrigatória antes de qualquer outra tela.
- Senha nova com pelo menos 8 caracteres e diferente da atual.
- Trocar a senha encerra as sessões abertas em outros navegadores.

### REQ-03 · HU35 · Gerenciar usuários e papéis

Como gerente, quero criar contas e definir o papel de cada pessoa, para controlar quem faz o quê.

- Cria conta com senha provisória e troca obrigatória no primeiro acesso.
- Muda o papel ou inativa a conta; a mudança fica registrada na auditoria.
- O gerente não muda o próprio papel, e a loja nunca fica sem nenhum gerente ativo (validado dentro da transação).

### REQ-04 · HU31 · Navegação e feedback

Como usuário, quero um menu fixo e mensagens claras, para usar o sistema sem treinamento.

- Menu lateral com os 6 módulos, item ativo destacado.
- Toda operação mostra aviso de sucesso (verde) ou erro (vermelho) que some sozinho em 4 segundos.
- Exclusões e cancelamentos pedem confirmação.
- Erros de validação aparecem ao lado do campo.

## Regras de negócio envolvidas

- RN23: Confirmar, cancelar, ajustar estoque, inativar e mudar papel gravam `audit_event` na mesma transação
- RN24: Cada rota exige papel (seção 6); o front só esconde, quem barra é o back-end
- RN25: O gerente não muda o próprio papel e a loja nunca fica sem gerente ativo

## Fora de escopo

- Escopo por loja ou região (uma loja só).
- Descontos, formas de pagamento, nota fiscal, troca e devolução parcial.
