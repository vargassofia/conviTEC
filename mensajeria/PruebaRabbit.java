package cr.ac.tec.convitec.mensajeria;

import com.rabbitmq.client.*;
import java.nio.charset.StandardCharsets;

public class PruebaRabbit {
    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        try (Connection conn = factory.newConnection();
             Channel canal = conn.createChannel()) {

            canal.queueDeclare("prueba", false, false, true, null);
            canal.basicPublish("", "prueba", null,
                    "Hola desde ConviTEC".getBytes(StandardCharsets.UTF_8));

            GetResponse r = canal.basicGet("prueba", true);
            System.out.println("Recibido: " + new String(r.getBody(), StandardCharsets.UTF_8));
        }
    }
}