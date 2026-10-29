package br.senai.crmloja.modules.crm.planning;

import java.time.LocalDate;

// Conta do painel "O que vai faltar" para UMA peça (spec crm/11, regras RN43 a RN46). Sem banco e sem relógio: tudo
// chega pronto, para o teste unitário cobrir cada caso e a Talita repetir a mesma conta que a tela mostra.
//
// Em palavras simples:
//  - média por dia = o que vendeu nos últimos `windowDays` dias ÷ `windowDays`;
//  - dias até acabar = estoque ÷ média (arredondado para baixo); sem venda no período, não dá para prever;
//  - quanto pedir = o que deve vender em `horizonDays` dias + estoque mínimo + chamados abertos da peça,
//    menos o que já tem e o que já está a caminho (nunca negativo).
public record Coverage(double dailyAvg, Integer daysLeft, LocalDate runOutDate, int suggestedQty, Status status,
                       boolean covered, boolean arrivesAfterRunOut) {

    // Ordem da lista: o mais urgente primeiro.
    public enum Status {
        OUT,        // zerado
        CRITICAL,   // acaba em até 7 dias, ou já está no estoque mínimo ou abaixo
        ATTENTION,  // acaba em até 21 dias
        OK,
        STALE,      // tem estoque e não vendeu nada no período: dinheiro parado
        NEW         // cadastrado há menos tempo que a janela e ainda sem venda: cedo demais para julgar
    }

    static final int CRITICAL_DAYS = 7;
    static final int ATTENTION_DAYS = 21;

    // daysInCatalog: há quantos dias a peça existe. Peça mais nova que a janela e sem venda é NEW, não STALE (revisão
    // de 29/10/2026: uma peça cadastrada hoje aparecia como dinheiro parado).
    public static Coverage of(int stock, int minStock, int soldInWindow, int windowDays, int incoming,
                              LocalDate nextArrival, int openRequestsQty, int horizonDays, LocalDate today) {
        return of(stock, minStock, soldInWindow, windowDays, incoming, nextArrival, openRequestsQty, horizonDays, today, windowDays);
    }

    public static Coverage of(int stock, int minStock, int soldInWindow, int windowDays, int incoming,
                              LocalDate nextArrival, int openRequestsQty, int horizonDays, LocalDate today,
                              int daysInCatalog) {
        double avg = windowDays <= 0 ? 0 : (double) soldInWindow / windowDays;
        Integer daysLeft = avg > 0 ? (int) Math.floor(stock / avg) : null;
        LocalDate runOut = daysLeft == null ? null : today.plusDays(daysLeft);

        int need = (int) Math.ceil(avg * horizonDays) + minStock + openRequestsQty;
        int suggested = Math.max(0, need - stock - incoming);

        Status status;
        if (stock == 0) status = Status.OUT;
        else if (soldInWindow == 0) status = daysInCatalog < windowDays ? Status.NEW : Status.STALE;
        else if (daysLeft <= CRITICAL_DAYS || stock <= minStock) status = Status.CRITICAL;
        else if (daysLeft <= ATTENTION_DAYS) status = Status.ATTENTION;
        else status = Status.OK;

        // O que já foi pedido resolve a necessidade? E chega antes de acabar?
        boolean covered = incoming > 0 && suggested == 0;
        boolean late = incoming > 0 && nextArrival != null
                && (stock == 0 || (runOut != null && nextArrival.isAfter(runOut)));
        return new Coverage(avg, daysLeft, runOut, suggested, status, covered, late);
    }

    // Precisa de ação de compra: está faltando ou vai faltar, e o que está a caminho não basta.
    public boolean needsOrder() {
        return suggestedQty > 0 && (status == Status.OUT || status == Status.CRITICAL || status == Status.ATTENTION);
    }
}
