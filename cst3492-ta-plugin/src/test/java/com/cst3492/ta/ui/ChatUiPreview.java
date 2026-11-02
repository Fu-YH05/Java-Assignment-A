package com.cst3492.ta.ui;

import com.cst3492.ta.citation.CitationVerifier;
import com.cst3492.ta.model.KnowledgeChunk;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.List;

/** Runs the production Swing component with controlled callbacks; never uses a project or credentials. */
public final class ChatUiPreview {
    private static ChatView view;
    private static JFrame window;
    private static boolean dark;
    private static int width = 400;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        SwingUtilities.invokeLater(() -> {
            window = new JFrame("CST3492 TA · Offline UI verification");
            window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            JMenuBar menu = new JMenuBar();
            JMenu cases = new JMenu("States");
            for (String name : List.of("Empty", "Course answer", "General answer", "Waiting", "Error")) {
                JMenuItem item = new JMenuItem(name);
                item.addActionListener(event -> show(name));
                cases.add(item);
            }
            JMenu theme = new JMenu("Theme");
            for (String name : List.of("Light", "Dark")) {
                JMenuItem item = new JMenuItem(name);
                item.addActionListener(event -> { dark = name.equals("Dark"); show("Course answer"); });
                theme.add(item);
            }
            JMenu size = new JMenu("Width");
            for (int pixels : List.of(400, 600)) {
                JMenuItem item = new JMenuItem(pixels + " px");
                item.addActionListener(event -> { width = pixels; show("Course answer"); });
                size.add(item);
            }
            JMenu service = new JMenu("Local reply");
            JMenuItem success = new JMenuItem("Complete pending request");
            success.addActionListener(event -> courseAnswer());
            service.add(success);
            JMenuItem fail = new JMenuItem("Fail pending request (502)");
            fail.addActionListener(event -> view.fail("embedding endpoint returned HTTP 502: 服务当前繁忙，请稍后重试"));
            service.add(fail);
            menu.add(cases); menu.add(theme); menu.add(size); menu.add(service);
            window.setJMenuBar(menu);
            show("Course answer");
            window.setLocation(80, 60);
            window.setVisible(true);
        });
    }

    private static void show(String state) {
        theme();
        view = new ChatView((question, code) -> { }, () -> {
            view.finishIndex("3 documents updated. The course index is ready.", null);
            view.setIndexStatus("3 documents · 48 chunks", true);
        }, () -> JOptionPane.showMessageDialog(window, "The production Settings action opens CST3492 TA settings."), ignored -> { });
        view.setModel("deepseek-flash");
        view.setCodeFont(new Font("Consolas", Font.PLAIN, 14));
        view.setIndexStatus("3 documents · 48 chunks", true);
        if (!state.equals("Empty")) {
            view.askQuestion("请结合课程材料解释依赖注入，并给出一个 Java 示例。", "class OrderService {\n    private PaymentGateway gateway;\n}");
            switch (state) {
                case "Course answer" -> courseAnswer();
                case "General answer" -> view.answer("Response is based on general knowledge; no specific course material is referenced.\n\n"
                        + "## Java 中的依赖注入\n\n通过构造方法传入依赖，使对象更容易进行单元测试。", List.of(), false);
                case "Error" -> view.fail("embedding endpoint returned HTTP 502: 服务当前繁忙，请稍后重试");
                default -> { }
            }
        } else {
            view.setIndexStatus("No course index", false);
        }
        window.setContentPane(view);
        view.setPreferredSize(new Dimension(width, 650));
        window.pack();
        window.revalidate();
    }

    private static void courseAnswer() {
        List<KnowledgeChunk> sources = List.of(source("Chapter_01-new (1)", 6), source("第2章 依赖注入", 12),
                source("Chapter_03-new", 13));
        String answer = "## 依赖注入：由外部提供协作对象\n\n"
                + "**依赖注入（DI）**让对象通过外部传入的依赖完成工作。课程强调将对象的创建与使用分离，"
                + "这样能够降低耦合，也能让单元测试更直接。{{cite:Chapter_01-new (1)#6}}\n\n"
                + "### 为什么使用构造方法？\n\n"
                + "- **依赖明确**：创建对象时即可看到它需要的组件。\n"
                + "- **便于测试**：用测试替身替换真实支付网关。\n"
                + "- **对象完整**：实例创建后即可正常工作。{{cite:第2章 依赖注入#12}}\n\n"
                + "```java\npublic final class OrderService {\n    private final PaymentGateway gateway;\n\n"
                + "    public OrderService(PaymentGateway gateway) {\n        this.gateway = gateway;\n    }\n"
                + "}\n```\n\n"
                + "### 与选中代码的关系\n\n"
                + "将 `gateway` 通过构造方法传入，即可在测试中提供模拟实现，无需连接真实支付服务。"
                + "{{cite:Chapter_03-new#13}}\n\n"
                + "把代码中的依赖关系显式表达出来，可以让团队更容易理解对象的责任、测试边界与组件协作方式。"
                + "当你继续扩展订单处理逻辑时，也可以沿用这一结构，在保持业务语义清晰的同时减少不必要的耦合。";
        view.answer(CitationVerifier.verify(answer, sources), sources, true);
    }

    private static KnowledgeChunk source(String name, int page) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.docId = name; c.docName = name + ".pdf"; c.pageFrom = page; c.pageTo = page;
        return c;
    }

    private static void theme() {
        ColorUIResource background = new ColorUIResource(dark ? 0x2b2d30 : 0xf7f8fa);
        ColorUIResource foreground = new ColorUIResource(dark ? 0xdfe1e5 : 0x202124);
        for (String key : List.of("Panel.background", "Viewport.background", "TextArea.background", "TextField.background",
                "Button.background", "ToggleButton.background"))
            UIManager.put(key, background);
        for (String key : List.of("Label.foreground", "Button.foreground", "ToggleButton.foreground", "TextArea.foreground",
                "TextField.foreground", "EditorPane.foreground", "TextArea.caretForeground"))
            UIManager.put(key, foreground);
        UIManager.put("Button.gradient", List.of(0f, 0f, background, background, background));
        UIManager.put("ToggleButton.gradient", List.of(0f, 0f, background, background, background));
        UIManager.put("Separator.foreground", new ColorUIResource(dark ? 0x45474a : 0xdfe2e6));
        UIManager.put("Link.activeForeground", new ColorUIResource(dark ? 0x8ab4f8 : 0x245fbd));
        for (String key : List.of("Label.font", "Button.font", "ToggleButton.font", "TextArea.font"))
            UIManager.put(key, new FontUIResource("Microsoft YaHei UI", Font.PLAIN, 13));
    }
}
