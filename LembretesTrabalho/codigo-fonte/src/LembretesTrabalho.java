import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Lembretes de Trabalho
 * - Lembretes de ponto, remédios e pausas (popup pequeno, sempre no topo, com som discreto)
 * - Anotações de reuniões de CS
 * - Lista de clientes que precisam de acompanhamento
 *
 * Dados salvos em: %USERPROFILE%\LembretesTrabalho\dados.bin
 */
public class LembretesTrabalho {

    // ---------- Tema azul / branco ----------
    static final Color AZUL = new Color(0x1E5AA8);
    static final Color AZUL_ESCURO = new Color(0x123F7A);
    static final Color AZUL_CLARO = new Color(0xE8F0FB);
    static final Color AZUL_LINHA = new Color(0xC9D9F0);
    static final Color BRANCO = Color.WHITE;
    static final Color DESTAQUE = new Color(0xFFF4D6); // clientes com contato vencido/hoje
    static final Font FONTE = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font FONTE_B = new Font("Segoe UI", Font.BOLD, 13);

    static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm");
    static final File PASTA = new File(System.getProperty("user.home"), "LembretesTrabalho");
    static final File ARQUIVO = new File(PASTA, "dados.bin");

    static final String[] TIPOS = {"Ponto", "Remédio", "Pausa"};
    static final String[] PRIORIDADES = {"Alta", "Média", "Baixa"};
    static final String[] STATUS = {"Em acompanhamento", "Aguardando retorno", "Resolvido"};

    // ---------- Modelos de dados ----------
    static class Lembrete implements Serializable {
        private static final long serialVersionUID = 1L;
        String id = UUID.randomUUID().toString();
        String tipo = "Ponto";
        String hora = "08:00";
        String mensagem = "";
        boolean diasUteis = true;
        boolean ativo = true;

        Lembrete() {}
        Lembrete(String tipo, String hora, String mensagem, boolean ativo) {
            this.tipo = tipo; this.hora = hora; this.mensagem = mensagem; this.ativo = ativo;
        }
    }

    static class Reuniao implements Serializable {
        private static final long serialVersionUID = 1L;
        String data = LocalDate.now().format(DF);
        String cliente = "", participantes = "", assunto = "", notas = "", proximosPassos = "";
        // v2: horário e avisos automáticos (campos novos ficam nulos/false em dados antigos)
        String hora = "";
        boolean lembrar1h = true, lembrar5m = true;
        String aviso1h = "", aviso5m = ""; // "data hora" para a qual o aviso já foi mostrado

        LocalDateTime inicio() {
            LocalDate d = data(data);
            if (d == null || hora == null || !horaValida(hora)) return null;
            return d.atTime(LocalTime.parse(hora, TF));
        }
        String chave() { return data + " " + hora; }
    }

    static class Cliente implements Serializable {
        private static final long serialVersionUID = 1L;
        String nome = "", contato = "", prioridade = "Média", proximoContato = "",
               status = "Em acompanhamento", observacoes = "";
    }

    static class Dados implements Serializable {
        private static final long serialVersionUID = 1L;
        List<Lembrete> lembretes = new ArrayList<>();
        List<Reuniao> reunioes = new ArrayList<>();
        List<Cliente> clientes = new ArrayList<>();
        boolean som = true;
    }

    static Dados dados;

    static void carregar() {
        if (ARQUIVO.exists()) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(ARQUIVO))) {
                dados = (Dados) in.readObject();
                for (Reuniao r : dados.reunioes) { // completa campos novos em reuniões antigas
                    if (r.hora == null) r.hora = "";
                    if (r.aviso1h == null) r.aviso1h = "";
                    if (r.aviso5m == null) r.aviso5m = "";
                }
                return;
            } catch (Exception e) {
                // faz backup do arquivo com problema e começa do zero
                ARQUIVO.renameTo(new File(PASTA, "dados_corrompido_" + System.currentTimeMillis() + ".bin"));
            }
        }
        dados = new Dados();
        dados.lembretes.add(new Lembrete("Ponto", "08:00", "Bater o ponto de entrada", true));
        dados.lembretes.add(new Lembrete("Ponto", "12:00", "Bater o ponto de saída para o almoço", true));
        dados.lembretes.add(new Lembrete("Ponto", "13:00", "Bater o ponto de volta do almoço", true));
        dados.lembretes.add(new Lembrete("Ponto", "18:00", "Bater o ponto de saída", true));
        dados.lembretes.add(new Lembrete("Pausa", "10:00", "Pausa: levante, alongue e beba água", true));
        dados.lembretes.add(new Lembrete("Pausa", "15:30", "Pausa: descanse a vista por alguns minutos", true));
        dados.lembretes.add(new Lembrete("Remédio", "09:00", "Tomar remédio (edite nome e horário)", false));
        salvar();
    }

    static void salvar() {
        try {
            PASTA.mkdirs();
            File tmp = new File(PASTA, "dados.tmp");
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(tmp))) {
                out.writeObject(dados);
            }
            ARQUIVO.delete();
            tmp.renameTo(ARQUIVO);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Erro ao salvar dados: " + e.getMessage());
        }
    }

    // ---------- Componentes visuais ----------
    static JButton botao(String texto, boolean primario) {
        JButton b = new JButton(texto);
        b.setUI(new BasicButtonUI());
        b.setFont(FONTE_B);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBackground(primario ? AZUL : BRANCO);
        b.setForeground(primario ? BRANCO : AZUL);
        b.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AZUL, 1, true), new EmptyBorder(6, 14, 6, 14)));
        Color normal = b.getBackground();
        Color hover = primario ? AZUL_ESCURO : AZUL_CLARO;
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(hover); }
            public void mouseExited(MouseEvent e) { b.setBackground(normal); }
        });
        return b;
    }

    static class Modelo<T> extends AbstractTableModel {
        final String[] colunas;
        final Supplier<List<T>> fonte;
        final Function<T, Object[]> linha;
        List<T> cache = new ArrayList<>();

        Modelo(String[] colunas, Supplier<List<T>> fonte, Function<T, Object[]> linha) {
            this.colunas = colunas; this.fonte = fonte; this.linha = linha; atualizar();
        }
        void atualizar() { cache = fonte.get(); fireTableDataChanged(); }
        T get(int r) { return r >= 0 && r < cache.size() ? cache.get(r) : null; }
        public int getRowCount() { return cache.size(); }
        public int getColumnCount() { return colunas.length; }
        public String getColumnName(int c) { return colunas[c]; }
        public Object getValueAt(int r, int c) { return linha.apply(cache.get(r))[c]; }
    }

    static JTable tabela(AbstractTableModel m) {
        JTable t = new JTable(m);
        t.setFont(FONTE);
        t.setRowHeight(28);
        t.setShowVerticalLines(false);
        t.setGridColor(AZUL_LINHA);
        t.setSelectionBackground(AZUL);
        t.setSelectionForeground(BRANCO);
        t.setFillsViewportHeight(true);
        t.setBackground(BRANCO);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, v, s, f, r, c);
                l.setBackground(AZUL);
                l.setForeground(BRANCO);
                l.setFont(FONTE_B);
                l.setBorder(new EmptyBorder(6, 8, 6, 8));
                return l;
            }
        });
        return t;
    }

    static JScrollPane rolagem(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(new LineBorder(AZUL_LINHA));
        sp.getViewport().setBackground(BRANCO);
        return sp;
    }

    static JPanel barra(JComponent... comps) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        p.setBackground(BRANCO);
        for (JComponent c : comps) p.add(c);
        return p;
    }

    /** Formulário genérico: pares (rótulo, componente). Retorna true se o usuário clicou em Salvar. */
    static boolean formulario(Component pai, String titulo, Object... pares) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BRANCO);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.NORTHWEST;
        for (int i = 0; i < pares.length; i += 2) {
            g.gridy = i / 2;
            g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
            JLabel l = new JLabel(pares[i].toString());
            l.setFont(FONTE_B);
            l.setForeground(AZUL_ESCURO);
            p.add(l, g);
            g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
            JComponent c = (JComponent) pares[i + 1];
            c.setFont(FONTE);
            if (c instanceof JTextArea) {
                JTextArea ta = (JTextArea) c;
                ta.setLineWrap(true); ta.setWrapStyleWord(true);
                JScrollPane sp = new JScrollPane(ta);
                sp.setPreferredSize(new Dimension(420, ta.getRows() * 20 + 10));
                c = sp;
            } else {
                c.setPreferredSize(new Dimension(420, 28));
            }
            p.add(c, g);
        }
        UIManager.put("OptionPane.background", BRANCO);
        UIManager.put("Panel.background", BRANCO);
        int r = JOptionPane.showOptionDialog(pai, p, titulo, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, new Object[]{"Salvar", "Cancelar"}, "Salvar");
        return r == 0;
    }

    static boolean horaValida(String h) {
        try { LocalTime.parse(h, TF); return true; } catch (Exception e) { return false; }
    }

    static LocalDate data(String s) {
        try { return LocalDate.parse(s.trim(), DF); } catch (Exception e) { return null; }
    }

    // ---------- Popup de lembrete ----------
    static final List<JDialog> popups = new ArrayList<>();

    static void popup(String etiqueta, String titulo, String mensagem, Runnable adiar) {
        popup(etiqueta, titulo, mensagem, adiar, null, null);
    }

    static void popup(String etiqueta, String titulo, String mensagem, Runnable adiar,
                      String extraTexto, Runnable extraAcao) {
        JDialog d = new JDialog((Frame) null);
        d.setUndecorated(true);
        d.setAlwaysOnTop(true);
        d.setFocusableWindowState(false); // não rouba o foco de quem está digitando
        d.setType(Window.Type.UTILITY);    // não aparece na barra de tarefas

        JPanel p = new JPanel(new BorderLayout(10, 8));
        p.setBackground(BRANCO);
        p.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AZUL, 2), new EmptyBorder(10, 12, 10, 12)));

        JPanel topo = new JPanel(new BorderLayout(8, 0));
        topo.setOpaque(false);
        JLabel tag = new JLabel(" " + etiqueta.toUpperCase() + " ");
        tag.setOpaque(true);
        tag.setBackground(AZUL);
        tag.setForeground(BRANCO);
        tag.setFont(new Font("Segoe UI", Font.BOLD, 11));
        JLabel hora = new JLabel(LocalTime.now().format(TF));
        hora.setForeground(Color.GRAY);
        hora.setFont(FONTE);
        topo.add(tag, BorderLayout.WEST);
        topo.add(hora, BorderLayout.EAST);

        JLabel t = new JLabel("<html><div style='width:290px'><b>" + esc(titulo) + "</b><br>"
                + esc(mensagem).replace("\n", "<br>") + "</div></html>");
        t.setFont(FONTE);
        t.setForeground(AZUL_ESCURO);

        JPanel bts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        bts.setOpaque(false);
        if (extraTexto != null) {
            JButton x = botao(extraTexto, false);
            x.addActionListener(e -> extraAcao.run());
            bts.add(x);
        }
        if (adiar != null) {
            JButton a = botao("Adiar 5 min", false);
            a.addActionListener(e -> { fecharPopup(d); adiar.run(); });
            bts.add(a);
        }
        JButton ok = botao("OK", true);
        ok.addActionListener(e -> fecharPopup(d));
        bts.add(ok);

        p.add(topo, BorderLayout.NORTH);
        p.add(t, BorderLayout.CENTER);
        p.add(bts, BorderLayout.SOUTH);
        d.setContentPane(p);
        d.pack();

        popups.add(d);
        reposicionarPopups();
        d.setVisible(true);
        if (dados.som) tocarSom();
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static void fecharPopup(JDialog d) {
        popups.remove(d);
        d.dispose();
        reposicionarPopups();
    }

    /** Empilha os popups no canto inferior direito, acima da barra de tarefas. */
    static void reposicionarPopups() {
        Rectangle area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int y = area.y + area.height - 12;
        for (JDialog d : popups) {
            y -= d.getHeight();
            d.setLocation(area.x + area.width - d.getWidth() - 12, y);
            y -= 8;
        }
    }

    /** Dois tons curtos, suaves e em volume baixo. */
    static void tocarSom() {
        new Thread(() -> {
            try {
                float sr = 44100f;
                AudioFormat f = new AudioFormat(sr, 16, 1, true, false);
                try (SourceDataLine l = AudioSystem.getSourceDataLine(f)) {
                    l.open(f);
                    l.start();
                    tom(l, sr, 880, 130);
                    tom(l, sr, 1175, 200);
                    l.drain();
                }
            } catch (Exception e) {
                Toolkit.getDefaultToolkit().beep();
            }
        }, "som").start();
    }

    static void tom(SourceDataLine l, float sr, double hz, int ms) {
        int n = (int) (sr * ms / 1000);
        byte[] b = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double env = Math.min(1.0, Math.min(i / (sr * 0.01), (n - i) / (sr * 0.06)));
            short v = (short) (Math.sin(2 * Math.PI * hz * i / sr) * env * 0.12 * Short.MAX_VALUE);
            b[2 * i] = (byte) v;
            b[2 * i + 1] = (byte) (v >> 8);
        }
        l.write(b, 0, b.length);
    }

    // ---------- Agendador ----------
    static final Set<String> disparados = new HashSet<>();
    static final Map<Object, LocalDateTime> adiados = new LinkedHashMap<>();
    static final Map<Object, Runnable> acoesAdiadas = new HashMap<>();

    static void adiar(Object chave, Runnable acao) {
        adiados.put(chave, LocalDateTime.now().plusMinutes(5));
        acoesAdiadas.put(chave, acao);
    }

    static void verificar() {
        LocalDateTime agora = LocalDateTime.now();
        String hm = agora.format(TF);
        String hoje = agora.toLocalDate().toString();
        DayOfWeek dia = agora.getDayOfWeek();
        boolean fimDeSemana = dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
        if (disparados.size() > 1000) disparados.clear();

        for (Lembrete l : new ArrayList<>(dados.lembretes)) {
            if (!l.ativo || (l.diasUteis && fimDeSemana)) continue;
            if (hm.equals(l.hora) && disparados.add(l.id + "|" + hoje + "|" + hm)) disparar(l);
        }
        for (Map.Entry<Object, LocalDateTime> e : new ArrayList<>(adiados.entrySet())) {
            if (!agora.isBefore(e.getValue())) {
                adiados.remove(e.getKey());
                Runnable a = acoesAdiadas.remove(e.getKey());
                if (a != null) a.run();
            }
        }
        verificarReunioes(agora);
        // Aviso diário de clientes para acompanhar (às 09:00 e ao abrir o programa)
        if ((hm.equals("09:00") || !disparados.contains("inicio")) && disparados.add("clientes|" + hoje + "|" + hm)) {
            disparados.add("inicio");
            avisarClientes(false);
        }
    }

    static void disparar(Lembrete l) {
        String titulo;
        switch (l.tipo) {
            case "Remédio": titulo = "Hora do remédio"; break;
            case "Pausa": titulo = "Hora da pausa"; break;
            default: titulo = "Hora de bater o ponto";
        }
        popup(l.tipo, titulo, l.mensagem, () -> adiar(l, () -> disparar(l)));
    }

    /**
     * Avisos das reuniões de CS:
     *  - 1 hora antes: enviar confirmação (mostra entre 60 e 5 min antes, caso o PC tenha sido ligado depois)
     *  - 5 minutos antes: a reunião vai começar
     * Cada aviso aparece uma vez por data/horário (fica salvo, então não repete ao reabrir o programa).
     */
    static void verificarReunioes(LocalDateTime agora) {
        boolean mudou = false;
        for (Reuniao r : new ArrayList<>(dados.reunioes)) {
            LocalDateTime ini = r.inicio();
            if (ini == null) continue;
            String k = r.chave();
            if (r.lembrar1h && !k.equals(r.aviso1h)
                    && !agora.isBefore(ini.minusMinutes(60)) && agora.isBefore(ini.minusMinutes(5))) {
                r.aviso1h = k; mudou = true;
                avisoConfirmacao(r);
            }
            if (r.lembrar5m && !k.equals(r.aviso5m)
                    && !agora.isBefore(ini.minusMinutes(5)) && agora.isBefore(ini.plusMinutes(1))) {
                r.aviso5m = k; mudou = true;
                avisoInicio(r);
            }
        }
        if (mudou) salvar();
    }

    static String descricao(Reuniao r) {
        String s = r.cliente + " às " + r.hora;
        if (!r.assunto.isEmpty()) s += " — " + r.assunto;
        if (!r.participantes.isEmpty()) s += "\nParticipantes: " + r.participantes;
        return s;
    }

    static void avisoConfirmacao(Reuniao r) {
        if (!dados.reunioes.contains(r)) return; // reunião excluída enquanto estava adiada
        popup("Reunião CS", "Enviar confirmação da reunião (1h)", descricao(r),
                () -> adiar("conf|" + r.chave() + "|" + System.identityHashCode(r), () -> avisoConfirmacao(r)),
                "Copiar mensagem", () -> copiar(textoConfirmacao(r)));
    }

    static void avisoInicio(Reuniao r) {
        popup("Reunião CS", "A reunião começa em 5 minutos", descricao(r), null);
    }

    static String textoConfirmacao(Reuniao r) {
        LocalDate d = data(r.data);
        String quando = d != null && d.equals(LocalDate.now()) ? "hoje" : "no dia " + r.data;
        return "Olá! Passando para confirmar nossa reunião " + quando + " às " + r.hora
                + (r.assunto.isEmpty() ? "" : " sobre " + r.assunto) + ". Podemos manter o horário?";
    }

    static void copiar(String texto) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(texto), null);
        if (trayIcon != null) trayIcon.displayMessage("Lembretes de Trabalho",
                "Mensagem copiada. Cole no WhatsApp ou e-mail com Ctrl+V.", TrayIcon.MessageType.INFO);
    }

    static void avisarClientes(boolean mesmoSemNenhum) {
        LocalDate hoje = LocalDate.now();
        List<Cliente> pend = dados.clientes.stream()
                .filter(c -> !"Resolvido".equals(c.status))
                .filter(c -> { LocalDate d = data(c.proximoContato); return d != null && !d.isAfter(hoje); })
                .collect(Collectors.toList());
        if (pend.isEmpty() && !mesmoSemNenhum) return;
        String nomes = pend.stream().limit(4).map(c -> c.nome).collect(Collectors.joining(", "));
        if (pend.size() > 4) nomes += " e mais " + (pend.size() - 4);
        popup("Clientes", pend.size() + " cliente(s) para acompanhar hoje",
                pend.isEmpty() ? "Nenhum acompanhamento pendente." : nomes, null);
    }

    // ---------- Janela principal ----------
    static JFrame janela;
    static TrayIcon trayIcon;

    static void criarJanela() {
        janela = new JFrame("Lembretes de Trabalho");
        janela.setIconImage(icone(64));
        janela.setSize(980, 620);
        janela.setLocationRelativeTo(null);

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(BRANCO);

        JPanel cab = new JPanel(new BorderLayout());
        cab.setBackground(AZUL);
        cab.setBorder(new EmptyBorder(14, 18, 14, 18));
        JLabel tit = new JLabel("Lembretes de Trabalho");
        tit.setFont(new Font("Segoe UI", Font.BOLD, 20));
        tit.setForeground(BRANCO);
        JLabel sub = new JLabel("Ponto  •  Remédios  •  Pausas  •  Reuniões de CS  •  Clientes");
        sub.setFont(FONTE);
        sub.setForeground(new Color(0xD6E4F7));
        JPanel tx = new JPanel(new GridLayout(2, 1));
        tx.setOpaque(false);
        tx.add(tit); tx.add(sub);
        cab.add(tx, BorderLayout.WEST);
        JLabel relogio = new JLabel();
        relogio.setFont(new Font("Segoe UI", Font.BOLD, 22));
        relogio.setForeground(BRANCO);
        cab.add(relogio, BorderLayout.EAST);
        new javax.swing.Timer(1000, e -> relogio.setText(
                LocalDate.now().format(DF) + "   " + LocalTime.now().format(TF))).start();

        JTabbedPane abas = new JTabbedPane();
        abas.setFont(FONTE_B);
        abas.setBackground(AZUL_CLARO);
        abas.setForeground(AZUL_ESCURO);
        abas.addTab("  Lembretes  ", abaLembretes());
        abas.addTab("  Reuniões de CS  ", abaReunioes());
        abas.addTab("  Clientes para acompanhar  ", abaClientes());

        JPanel centro = new JPanel(new BorderLayout());
        centro.setBackground(BRANCO);
        centro.setBorder(new EmptyBorder(10, 10, 10, 10));
        centro.add(abas);

        raiz.add(cab, BorderLayout.NORTH);
        raiz.add(centro, BorderLayout.CENTER);
        janela.setContentPane(raiz);

        janela.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        janela.addWindowListener(new WindowAdapter() {
            boolean avisou = false;
            public void windowClosing(WindowEvent e) {
                if (trayIcon != null) {
                    janela.setVisible(false);
                    if (!avisou) {
                        trayIcon.displayMessage("Lembretes de Trabalho",
                                "Continuo rodando aqui perto do relógio. Clique duas vezes para abrir.",
                                TrayIcon.MessageType.INFO);
                        avisou = true;
                    }
                } else {
                    janela.setState(Frame.ICONIFIED); // sem bandeja: apenas minimiza
                }
            }
        });
    }

    static JPanel abaLembretes() {
        Modelo<Lembrete> m = new Modelo<>(new String[]{"Tipo", "Horário", "Mensagem", "Dias", "Ativo"},
                () -> dados.lembretes.stream().sorted(Comparator.comparing((Lembrete l) -> l.hora))
                        .collect(Collectors.toList()),
                l -> new Object[]{l.tipo, l.hora, l.mensagem, l.diasUteis ? "Seg a Sex" : "Todos os dias",
                        l.ativo ? "Sim" : "Não"});
        JTable t = tabela(m);
        t.getColumnModel().getColumn(0).setPreferredWidth(90);
        t.getColumnModel().getColumn(1).setPreferredWidth(70);
        t.getColumnModel().getColumn(2).setPreferredWidth(460);
        t.getColumnModel().getColumn(3).setPreferredWidth(110);
        t.getColumnModel().getColumn(4).setPreferredWidth(60);

        JButton novo = botao("+ Novo lembrete", true);
        JButton pausa = botao("+ Pausa rápida", false);
        JButton editar = botao("Editar", false);
        JButton ativar = botao("Ativar / Desativar", false);
        JButton excluir = botao("Excluir", false);
        JButton testar = botao("Testar popup", false);
        JCheckBox som = new JCheckBox("Som ativado", dados.som);
        som.setBackground(BRANCO);
        som.setFont(FONTE);
        som.addActionListener(e -> { dados.som = som.isSelected(); salvar(); });

        novo.addActionListener(e -> { Lembrete l = new Lembrete(); if (editarLembrete(l, "Novo lembrete")) { dados.lembretes.add(l); salvar(); m.atualizar(); } });
        pausa.addActionListener(e -> {
            Lembrete l = new Lembrete("Pausa", LocalTime.now().plusHours(1).format(TF), "Pausa: levante, alongue e beba água", true);
            if (editarLembrete(l, "Nova pausa")) { dados.lembretes.add(l); salvar(); m.atualizar(); }
        });
        Runnable ed = () -> { Lembrete l = m.get(t.getSelectedRow()); if (l != null && editarLembrete(l, "Editar lembrete")) { salvar(); m.atualizar(); } };
        editar.addActionListener(e -> ed.run());
        duploClique(t, ed);
        ativar.addActionListener(e -> { Lembrete l = m.get(t.getSelectedRow()); if (l != null) { l.ativo = !l.ativo; salvar(); m.atualizar(); } });
        excluir.addActionListener(e -> {
            Lembrete l = m.get(t.getSelectedRow());
            if (l != null && confirmar("Excluir o lembrete das " + l.hora + "?")) { dados.lembretes.remove(l); adiados.remove(l); acoesAdiadas.remove(l); salvar(); m.atualizar(); }
        });
        testar.addActionListener(e -> {
            Lembrete l = m.get(t.getSelectedRow());
            if (l == null) l = new Lembrete("Pausa", LocalTime.now().format(TF), "Este é um exemplo de lembrete.", true);
            disparar(l);
        });

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BRANCO);
        p.add(barra(novo, pausa, editar, ativar, excluir, testar, som), BorderLayout.NORTH);
        p.add(rolagem(t), BorderLayout.CENTER);
        p.add(dica("Dica: crie quantas pausas e remédios precisar. O popup aparece no canto da tela, sobre qualquer janela, sem roubar o foco."), BorderLayout.SOUTH);
        return p;
    }

    static boolean editarLembrete(Lembrete l, String titulo) {
        JComboBox<String> tipo = new JComboBox<>(TIPOS);
        tipo.setSelectedItem(l.tipo);
        JTextField hora = new JTextField(l.hora);
        JTextField msg = new JTextField(l.mensagem);
        JCheckBox uteis = new JCheckBox("Apenas de segunda a sexta", l.diasUteis);
        JCheckBox ativo = new JCheckBox("Ativo", l.ativo);
        uteis.setBackground(BRANCO); ativo.setBackground(BRANCO);
        while (formulario(janela, titulo, "Tipo", tipo, "Horário (HH:mm)", hora, "Mensagem", msg, "Dias", uteis, "", ativo)) {
            String h = hora.getText().trim();
            if (h.matches("\\d:\\d\\d")) h = "0" + h;
            if (!horaValida(h)) { aviso("Horário inválido. Use o formato HH:mm, por exemplo 14:30."); continue; }
            l.tipo = (String) tipo.getSelectedItem();
            l.hora = h;
            l.mensagem = msg.getText().trim().isEmpty() ? l.tipo : msg.getText().trim();
            l.diasUteis = uteis.isSelected();
            l.ativo = ativo.isSelected();
            return true;
        }
        return false;
    }

    static JPanel abaReunioes() {
        JTextField busca = new JTextField(22);
        busca.setFont(FONTE);
        Modelo<Reuniao> m = new Modelo<>(new String[]{"Data", "Hora", "Cliente", "Assunto", "Participantes", "Avisos", "Próximos passos"},
                () -> {
                    String q = busca.getText().trim().toLowerCase();
                    return dados.reunioes.stream()
                            .filter(r -> q.isEmpty() || (r.cliente + " " + r.assunto + " " + r.notas + " " + r.participantes + " " + r.proximosPassos).toLowerCase().contains(q))
                            .sorted(Comparator.comparing((Reuniao r) -> { LocalDate d = data(r.data); return d == null ? LocalDate.MIN : d; })
                                    .thenComparing(r -> r.hora).reversed())
                            .collect(Collectors.toList());
                },
                r -> new Object[]{r.data, r.hora, r.cliente, r.assunto, r.participantes,
                        r.inicio() == null ? "" : (r.lembrar1h ? "1h " : "") + (r.lembrar5m ? "5min" : ""),
                        r.proximosPassos.replace('\n', ' ')});
        JTable t = tabela(m);
        int[] larg = {85, 55, 150, 200, 150, 70, 230};
        for (int i = 0; i < larg.length; i++) t.getColumnModel().getColumn(i).setPreferredWidth(larg[i]);
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int row, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, v, s, f, row, c);
                l.setBorder(new EmptyBorder(0, 8, 0, 8));
                Reuniao r = m.get(row);
                LocalDateTime ini = r == null ? null : r.inicio();
                boolean hojeFutura = ini != null && ini.toLocalDate().equals(LocalDate.now()) && ini.isAfter(LocalDateTime.now());
                if (!s) { l.setBackground(hojeFutura ? AZUL_CLARO : BRANCO); l.setForeground(AZUL_ESCURO); }
                l.setFont(hojeFutura ? FONTE_B : FONTE);
                return l;
            }
        });
        new javax.swing.Timer(60_000, e -> t.repaint()).start();
        aoDigitar(busca, m::atualizar);

        JTextArea preview = new JTextArea(6, 40);
        preview.setEditable(false);
        preview.setFont(FONTE);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        preview.setBackground(AZUL_CLARO);
        preview.setForeground(AZUL_ESCURO);
        preview.setBorder(new EmptyBorder(8, 10, 8, 10));
        t.getSelectionModel().addListSelectionListener(e -> {
            Reuniao r = m.get(t.getSelectedRow());
            preview.setText(r == null ? "" : "ANOTAÇÕES:\n" + r.notas + "\n\nPRÓXIMOS PASSOS:\n" + r.proximosPassos);
            preview.setCaretPosition(0);
        });

        JButton nova = botao("+ Nova reunião", true);
        JButton editar = botao("Editar", false);
        JButton excluir = botao("Excluir", false);
        JButton exportar = botao("Exportar .txt", false);
        JLabel lb = new JLabel("Buscar:");
        lb.setFont(FONTE_B);
        lb.setForeground(AZUL_ESCURO);

        nova.addActionListener(e -> { Reuniao r = new Reuniao(); if (editarReuniao(r, "Nova reunião de CS")) { dados.reunioes.add(r); salvar(); m.atualizar(); } });
        Runnable ed = () -> { Reuniao r = m.get(t.getSelectedRow()); if (r != null && editarReuniao(r, "Editar reunião")) { salvar(); m.atualizar(); } };
        editar.addActionListener(e -> ed.run());
        duploClique(t, ed);
        excluir.addActionListener(e -> {
            Reuniao r = m.get(t.getSelectedRow());
            if (r != null && confirmar("Excluir a reunião com " + r.cliente + " (" + r.data + ")?")) { dados.reunioes.remove(r); salvar(); m.atualizar(); preview.setText(""); }
        });
        exportar.addActionListener(e -> exportarReunioes(m.cache));

        JScrollPane sp = rolagem(preview);
        sp.setPreferredSize(new Dimension(100, 150));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rolagem(t), sp);
        split.setResizeWeight(0.65);
        split.setBorder(null);
        split.setBackground(BRANCO);

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BRANCO);
        p.add(barra(nova, editar, excluir, exportar, lb, busca), BorderLayout.NORTH);
        p.add(split, BorderLayout.CENTER);
        p.add(dica("Reuniões de hoje que ainda vão acontecer ficam destacadas. Avisos: 1h antes (enviar confirmação) e 5 min antes do início."), BorderLayout.SOUTH);
        return p;
    }

    static boolean editarReuniao(Reuniao r, String titulo) {
        JTextField dt = new JTextField(r.data);
        JTextField hr = new JTextField(r.hora);
        JCheckBox l1h = new JCheckBox("Avisar 1 hora antes para enviar a confirmação", r.lembrar1h);
        JCheckBox l5m = new JCheckBox("Avisar 5 minutos antes do início", r.lembrar5m);
        l1h.setBackground(BRANCO); l5m.setBackground(BRANCO);
        JTextField cli = new JTextField(r.cliente);
        JTextField part = new JTextField(r.participantes);
        JTextField ass = new JTextField(r.assunto);
        JTextArea notas = new JTextArea(r.notas, 8, 40);
        JTextArea passos = new JTextArea(r.proximosPassos, 4, 40);
        JCheckBox acomp = new JCheckBox("Adicionar/atualizar cliente na lista de acompanhamento", false);
        acomp.setBackground(BRANCO);
        JTextField prox = new JTextField(LocalDate.now().plusDays(7).format(DF));
        while (formulario(janela, titulo, "Data (dd/mm/aaaa)", dt, "Horário (HH:mm)", hr, "Lembretes", l1h, "", l5m,
                "Cliente", cli, "Participantes", part, "Assunto", ass,
                "Anotações", notas, "Próximos passos", passos, "", acomp, "Próximo contato", prox)) {
            if (data(dt.getText()) == null) { aviso("Data inválida. Use dd/mm/aaaa."); continue; }
            String h = hr.getText().trim();
            if (h.matches("\\d:\\d\\d")) h = "0" + h;
            if (!h.isEmpty() && !horaValida(h)) { aviso("Horário inválido. Use HH:mm, por exemplo 14:30 (ou deixe em branco)."); continue; }
            r.hora = h;
            r.lembrar1h = l1h.isSelected();
            r.lembrar5m = l5m.isSelected();
            if (cli.getText().trim().isEmpty()) { aviso("Informe o cliente."); continue; }
            if (acomp.isSelected() && data(prox.getText()) == null) { aviso("Data do próximo contato inválida."); continue; }
            r.data = dt.getText().trim();
            r.cliente = cli.getText().trim();
            r.participantes = part.getText().trim();
            r.assunto = ass.getText().trim();
            r.notas = notas.getText();
            r.proximosPassos = passos.getText();
            if (acomp.isSelected()) {
                Cliente c = dados.clientes.stream().filter(x -> x.nome.equalsIgnoreCase(r.cliente)).findFirst().orElse(null);
                if (c == null) { c = new Cliente(); c.nome = r.cliente; dados.clientes.add(c); }
                c.proximoContato = prox.getText().trim();
                if ("Resolvido".equals(c.status)) c.status = "Em acompanhamento";
                if (!r.proximosPassos.trim().isEmpty()) c.observacoes = "Reunião " + r.data + ": " + r.proximosPassos.trim();
                if (modeloClientes != null) modeloClientes.atualizar();
            }
            return true;
        }
        return false;
    }

    static void exportarReunioes(List<Reuniao> lista) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("reunioes_cs_" + LocalDate.now() + ".txt"));
        if (fc.showSaveDialog(janela) != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fc.getSelectedFile()), "UTF-8"))) {
            for (Reuniao r : lista) {
                w.println("==================================================");
                w.println(r.data + " - " + r.cliente + " - " + r.assunto);
                w.println("Participantes: " + r.participantes);
                w.println();
                w.println("Anotações:");
                w.println(r.notas);
                w.println();
                w.println("Próximos passos:");
                w.println(r.proximosPassos);
                w.println();
            }
            aviso("Exportado: " + fc.getSelectedFile().getAbsolutePath());
        } catch (IOException ex) {
            aviso("Erro ao exportar: " + ex.getMessage());
        }
    }

    static Modelo<Cliente> modeloClientes;

    static JPanel abaClientes() {
        JCheckBox ocultar = new JCheckBox("Ocultar resolvidos", true);
        ocultar.setBackground(BRANCO);
        ocultar.setFont(FONTE);
        Modelo<Cliente> m = new Modelo<>(new String[]{"Cliente", "Contato", "Prioridade", "Próximo contato", "Status", "Observações"},
                () -> dados.clientes.stream()
                        .filter(c -> !ocultar.isSelected() || !"Resolvido".equals(c.status))
                        .sorted(Comparator.comparing((Cliente c) -> { LocalDate d = data(c.proximoContato); return d == null ? LocalDate.MAX : d; })
                                .thenComparing(c -> Arrays.asList(PRIORIDADES).indexOf(c.prioridade)))
                        .collect(Collectors.toList()),
                c -> new Object[]{c.nome, c.contato, c.prioridade, c.proximoContato, c.status, c.observacoes.replace('\n', ' ')});
        modeloClientes = m;
        JTable t = tabela(m);
        t.getColumnModel().getColumn(0).setPreferredWidth(170);
        t.getColumnModel().getColumn(1).setPreferredWidth(150);
        t.getColumnModel().getColumn(2).setPreferredWidth(80);
        t.getColumnModel().getColumn(3).setPreferredWidth(110);
        t.getColumnModel().getColumn(4).setPreferredWidth(140);
        t.getColumnModel().getColumn(5).setPreferredWidth(300);
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, v, s, f, r, c);
                l.setBorder(new EmptyBorder(0, 8, 0, 8));
                Cliente cl = m.get(r);
                LocalDate d = cl == null ? null : data(cl.proximoContato);
                boolean vencido = d != null && !d.isAfter(LocalDate.now()) && !"Resolvido".equals(cl.status);
                if (!s) {
                    l.setBackground(vencido ? DESTAQUE : BRANCO);
                    l.setForeground(AZUL_ESCURO);
                }
                l.setFont(vencido || (c == 2 && "Alta".equals(v)) ? FONTE_B : FONTE);
                return l;
            }
        });
        ocultar.addActionListener(e -> m.atualizar());

        JButton novo = botao("+ Novo cliente", true);
        JButton editar = botao("Editar", false);
        JButton resolvido = botao("Marcar resolvido", false);
        JButton excluir = botao("Excluir", false);
        JButton hoje = botao("Quem acompanhar hoje?", false);

        novo.addActionListener(e -> { Cliente c = new Cliente(); c.proximoContato = LocalDate.now().format(DF); if (editarCliente(c, "Novo cliente")) { dados.clientes.add(c); salvar(); m.atualizar(); } });
        Runnable ed = () -> { Cliente c = m.get(t.getSelectedRow()); if (c != null && editarCliente(c, "Editar cliente")) { salvar(); m.atualizar(); } };
        editar.addActionListener(e -> ed.run());
        duploClique(t, ed);
        resolvido.addActionListener(e -> { Cliente c = m.get(t.getSelectedRow()); if (c != null) { c.status = "Resolvido"; salvar(); m.atualizar(); } });
        excluir.addActionListener(e -> {
            Cliente c = m.get(t.getSelectedRow());
            if (c != null && confirmar("Excluir " + c.nome + " da lista?")) { dados.clientes.remove(c); salvar(); m.atualizar(); }
        });
        hoje.addActionListener(e -> avisarClientes(true));

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BRANCO);
        p.add(barra(novo, editar, resolvido, excluir, hoje, ocultar), BorderLayout.NORTH);
        p.add(rolagem(t), BorderLayout.CENTER);
        p.add(dica("Linhas destacadas: contato para hoje ou atrasado. Todo dia às 09:00 (e ao abrir o programa) aparece um aviso com os pendentes."), BorderLayout.SOUTH);
        return p;
    }

    static boolean editarCliente(Cliente c, String titulo) {
        JTextField nome = new JTextField(c.nome);
        JTextField contato = new JTextField(c.contato);
        JComboBox<String> prio = new JComboBox<>(PRIORIDADES);
        prio.setSelectedItem(c.prioridade);
        JTextField prox = new JTextField(c.proximoContato);
        JComboBox<String> st = new JComboBox<>(STATUS);
        st.setSelectedItem(c.status);
        JTextArea obs = new JTextArea(c.observacoes, 6, 40);
        while (formulario(janela, titulo, "Cliente", nome, "Contato (nome/tel/e-mail)", contato, "Prioridade", prio,
                "Próximo contato (dd/mm/aaaa)", prox, "Status", st, "Observações", obs)) {
            if (nome.getText().trim().isEmpty()) { aviso("Informe o nome do cliente."); continue; }
            if (!prox.getText().trim().isEmpty() && data(prox.getText()) == null) { aviso("Data inválida. Use dd/mm/aaaa ou deixe em branco."); continue; }
            c.nome = nome.getText().trim();
            c.contato = contato.getText().trim();
            c.prioridade = (String) prio.getSelectedItem();
            c.proximoContato = prox.getText().trim();
            c.status = (String) st.getSelectedItem();
            c.observacoes = obs.getText();
            return true;
        }
        return false;
    }

    // ---------- Utilitários ----------
    static JLabel dica(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        l.setForeground(AZUL);
        l.setBorder(new EmptyBorder(8, 4, 0, 4));
        return l;
    }

    static void duploClique(JTable t, Runnable r) {
        t.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2 && t.getSelectedRow() >= 0) r.run(); }
        });
    }

    static void aoDigitar(JTextField f, Runnable r) {
        f.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { r.run(); }
            public void removeUpdate(DocumentEvent e) { r.run(); }
            public void changedUpdate(DocumentEvent e) { r.run(); }
        });
    }

    static boolean confirmar(String msg) {
        return JOptionPane.showConfirmDialog(janela, msg, "Confirmar", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    static void aviso(String msg) { JOptionPane.showMessageDialog(janela, msg); }

    static Image icone(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(AZUL);
        g.fillOval(0, 0, s - 1, s - 1);
        g.setColor(BRANCO);
        g.setStroke(new BasicStroke(Math.max(1.5f, s / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int c = s / 2;
        g.drawOval(s / 5, s / 5, s * 3 / 5, s * 3 / 5);
        g.drawLine(c, c, c, s / 3);
        g.drawLine(c, c, c + s / 6, c);
        g.dispose();
        return img;
    }

    static void criarBandeja() {
        if (!SystemTray.isSupported()) return;
        PopupMenu menu = new PopupMenu();
        MenuItem abrir = new MenuItem("Abrir");
        MenuItem sair = new MenuItem("Sair");
        abrir.addActionListener(e -> mostrarJanela());
        sair.addActionListener(e -> { salvar(); System.exit(0); });
        menu.add(abrir);
        menu.addSeparator();
        menu.add(sair);
        trayIcon = new TrayIcon(icone(16), "Lembretes de Trabalho", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> mostrarJanela());
        try { SystemTray.getSystemTray().add(trayIcon); } catch (AWTException e) { trayIcon = null; }
    }

    static void mostrarJanela() {
        SwingUtilities.invokeLater(() -> {
            janela.setVisible(true);
            janela.setState(Frame.NORMAL);
            janela.toFront();
        });
    }

    static ServerSocket trava; // impede abrir duas cópias

    public static void main(String[] args) {
        try {
            trava = new ServerSocket(47321, 0, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "O Lembretes de Trabalho já está aberto (veja o ícone perto do relógio).");
            return;
        }
        boolean minimizado = Arrays.asList(args).contains("--minimizado");
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}
            UIManager.put("TabbedPane.selected", BRANCO);
            UIManager.put("TabbedPane.contentAreaColor", BRANCO);
            UIManager.put("TabbedPane.focus", AZUL);
            UIManager.put("Panel.background", BRANCO);
            UIManager.put("OptionPane.background", BRANCO);
            UIManager.put("ComboBox.background", BRANCO);
            UIManager.put("CheckBox.background", BRANCO);
            UIManager.put("ToolTip.background", AZUL_CLARO);
            carregar();
            criarJanela();
            criarBandeja();
            if (!minimizado || trayIcon == null) janela.setVisible(true);
            javax.swing.Timer t = new javax.swing.Timer(10_000, e -> verificar());
            t.setInitialDelay(2_000);
            t.start();
        });
    }
}
