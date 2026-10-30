package br.senai.crmloja.core.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

// Envia o link de "Esqueci minha senha". Com SMTP configurado (SMTP_HOST, SMTP_USER, SMTP_PASSWORD, MAIL_FROM),
// manda o e-mail. Sem SMTP: no perfil dev (localhost) o link aparece no log do back-end para testar; nos outros
// perfis só avisa que o e-mail não está configurado (o link nunca vai para o log de um servidor publicado).
@Component
public class RecoveryMailer {

    private static final Logger log = LoggerFactory.getLogger(RecoveryMailer.class);

    private final ObjectProvider<JavaMailSender> mail;
    private final Environment env;

    public RecoveryMailer(ObjectProvider<JavaMailSender> mail, Environment env) {
        this.mail = mail;
        this.env = env;
    }

    public boolean configured() {
        return mail.getIfAvailable() != null && !env.getProperty("spring.mail.host", "").isBlank();
    }

    public void sendReset(String to, String name, String link) {
        if (!configured()) {
            if (env.acceptsProfiles(Profiles.of("dev"))) {
                log.warn("E-mail não configurado (SMTP_HOST vazio). Link de recuperação para {}: {}", to, link);
            } else {
                log.warn("Pedido de recuperação de senha para {}, mas o e-mail não está configurado (SMTP_HOST).", to);
            }
            return;
        }
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(env.getProperty("app.mail-from", "Orden <nao-responda@orden.local>"));
        m.setTo(to);
        m.setSubject("Orden · redefinir sua senha");
        m.setText("Olá, " + name + "!\n\n"
                + "Recebemos um pedido para redefinir a senha da sua conta na Orden.\n"
                + "Abra o link abaixo em até 30 minutos para criar uma senha nova:\n\n" + link + "\n\n"
                + "Se não foi você, ignore este e-mail: sua senha continua a mesma.\n\nEquipe Orden");
        try {
            mail.getObject().send(m);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar o e-mail de recuperação para {}", to, e);
        }
    }
}
