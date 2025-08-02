package com.metaphorce.TaskFlow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class vista extends JFrame {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private String baseUrl = "http://localhost:8081/TaskFlow";
    private String authHeader = ""; // Basic auth

    private final JLabel statusBar = new JLabel("Listo");

    public vista() {
        super("Client TaskFlow");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 650);
        setLayout(new BorderLayout(6,6));

        // Top: configuración de URL y credenciales
        JPanel config = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField urlField = new JTextField(baseUrl, 25);
        JTextField userField = new JTextField("tester", 10);
        JPasswordField passField = new JPasswordField("pass123", 10);
        JButton applyBtn = new JButton("Aplicar");
        config.add(new JLabel("Usuario:"));
        config.add(userField);
        config.add(new JLabel("Contraseña:"));
        config.add(passField);
        config.add(applyBtn);
        add(config, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();

        tabs.addTab("Usuarios", buildEntityPanel(
                "Usuarios",
                () -> baseUrl + "/usuarios",
                new String[]{"idUsuario", "nombre", "correo"},
                Map.of("nombre", "", "correo", ""),
                null // sin enums
        ));

        // Tareas con combos de enums
        tabs.addTab("Tareas", buildEntityPanel(
                "Tareas",
                () -> baseUrl + "/tareas",
                new String[]{"idTarea", "titulo", "prioridad", "estatus"},
                Map.of("titulo", "", "prioridad", "BAJA", "estatus", "PENDIENTE"),
                Map.of(
                        "prioridad", new String[]{"BAJA", "MEDIA", "ALTA"},
                        "estatus", new String[]{"PENDIENTE", "EN_PROGRESO", "COMPLETADA"}
                )
        ));

        tabs.addTab("Proyectos", buildEntityPanel(
                "Proyectos",
                () -> baseUrl + "/proyectos",
                new String[]{"idProyecto", "nombre", "descripcion"},
                Map.of("nombre", "", "descripcion", ""),
                null
        ));

        // Gestión Tiempo: asume ISO datetimes
        tabs.addTab("Gestión Tiempo", buildEntityPanel(
                "GestionTiempo",
                () -> baseUrl + "/gestiontiempo",
                new String[]{"idGestion", "idUsuario", "idTarea", "fechaInicio", "fechaFin"},
                Map.of(
                        "idUsuario", "1",
                        "idTarea", "1",
                        "fechaInicio", LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "fechaFin", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                ),
                null
        ));

        add(tabs, BorderLayout.CENTER);

        // Status bar
        JPanel bottom = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
        bottom.add(statusBar, BorderLayout.WEST);
        add(bottom, BorderLayout.SOUTH);

        // Aplicar configuración inicial y refrescar
        applyBtn.addActionListener(e -> {
            baseUrl = urlField.getText().trim();
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword());
            String creds = user + ":" + pass;
            String encoded = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
            authHeader = "Basic " + encoded;
            setStatus("Configuración aplicada.");
            // refrescar todas las pestañas
            for (int i = 0; i < tabs.getTabCount(); i++) {
                Component c = tabs.getComponentAt(i);
                refreshIfPossible(c);
            }
        });
        applyBtn.doClick(); // inicial

        setVisible(true);
    }

    private void refreshIfPossible(Component c) {
        if (c instanceof JPanel panel) {
            for (Component sub : panel.getComponents()) {
                if (sub instanceof JButton btn && btn.getText().equals("Refrescar")) {
                    btn.doClick();
                }
            }
        }
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusBar.setText(msg));
    }

    /**
     * Construye un panel genérico capaz de listar y crear entidades.
     */
    private JPanel buildEntityPanel(String title,
                                    UrlSupplier urlSupplier,
                                    String[] columns,
                                    Map<String, String> createFields,
                                    Map<String, String[]> enumOptions) {
        JPanel panel = new JPanel(new BorderLayout(6,6));

        DefaultTableModel tableModel = new DefaultTableModel(columns, 0);
        JTable table = new JTable(tableModel);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // formulario abajo
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        java.util.List<JComponent> inputs = new java.util.ArrayList<>();
        for (String key : createFields.keySet()) {
            form.add(new JLabel(key + ":"));
            if (enumOptions != null && enumOptions.containsKey(key)) {
                JComboBox<String> combo = new JComboBox<>(enumOptions.get(key));
                combo.setSelectedItem(createFields.get(key));
                inputs.add(combo);
                form.add(combo);
            } else {
                JTextField tf = new JTextField(createFields.get(key), 10);
                inputs.add(tf);
                form.add(tf);
            }
        }
        JButton createBtn = new JButton("Crear");
        JButton refreshBtn = new JButton("Refrescar");
        form.add(createBtn);
        form.add(refreshBtn);
        panel.add(form, BorderLayout.SOUTH);

        JLabel localStatus = new JLabel(" ");
        panel.add(localStatus, BorderLayout.NORTH);

        // Refresh action
        refreshBtn.addActionListener(e -> {
            new SwingWorker<List<Map<String, Object>>, Void>() {
                @Override
                protected List<Map<String, Object>> doInBackground() throws Exception {
                    setStatus("Cargando " + title + "...");
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(urlSupplier.get()))
                            .GET()
                            .header("Accept", "application/json")
                            .header("Authorization", authHeader)
                            .build();
                    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() != 200) {
                        throw new RuntimeException("HTTP " + resp.statusCode() + " -> " + resp.body());
                    }
                    return mapper.readValue(resp.body(), new TypeReference<>() {});
                }

                @Override
                protected void done() {
                    try {
                        List<Map<String, Object>> items = get();
                        tableModel.setRowCount(0);
                        for (Map<String, Object> item : items) {
                            Object[] row = new Object[columns.length];
                            for (int i = 0; i < columns.length; i++) {
                                row[i] = item.getOrDefault(columns[i], "");
                            }
                            tableModel.addRow(row);
                        }
                        localStatus.setText("Última actualización: " + java.time.LocalTime.now().withNano(0));
                    } catch (Exception ex) {
                        localStatus.setText("Error al cargar: " + ex.getMessage());
                    } finally {
                        setStatus("Listo");
                    }
                }
            }.execute();
        });

        // Create action
        createBtn.addActionListener(e -> {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    setStatus("Creando en " + title + "...");
                    Map<String, Object> payload = new HashMap<>();
                    int idx = 0;
                    for (String key : createFields.keySet()) {
                        JComponent comp = inputs.get(idx);
                        String value;
                        if (comp instanceof JComboBox<?> cb) {
                            value = cb.getSelectedItem().toString();
                        } else if (comp instanceof JTextField tf) {
                            value = tf.getText().trim();
                        } else {
                            value = "";
                        }
                        payload.put(key, value);
                        idx++;
                    }
                    String json = mapper.writeValueAsString(payload);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(urlSupplier.get()))
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .header("Authorization", authHeader)
                            .build();
                    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                    if (!(resp.statusCode() == 200 || resp.statusCode() == 201)) {
                        throw new RuntimeException("HTTP " + resp.statusCode() + " -> " + resp.body());
                    }
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        localStatus.setText("Creado correctamente.");
                        refreshBtn.doClick();
                    } catch (Exception ex) {
                        localStatus.setText("Error al crear: " + ex.getMessage());
                    } finally {
                        setStatus("Listo");
                    }
                }
            }.execute();
        });

        // inicial
        refreshBtn.doClick();
        return panel;
    }

    private interface UrlSupplier {
        String get();
    }

    private void showError(String msg) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            vista client = new vista();
            client.setVisible(true);
        });
    }
}
