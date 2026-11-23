package com.mmm.smoke;

import com.google.gson.JsonParser;
import com.mmm.config.Configs;
import com.mmm.social.PublicChatClient;
import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Isolated client smoke checks. Start the loopback fixture before PublicChatClient initializes. */
final class ChatParityChecks
{
    private static final LinkedBlockingQueue<String> REQUESTS = new LinkedBlockingQueue<>();
    private static HttpServer server;
    private static String endpoint;

    static void start()
    {
        try { startFixture(); }
        catch (Exception error)
        {
            if (server != null) { server.stop(0); server = null; }
            throw new IllegalStateException("Could not start chat parity loopback fixture", error);
        }
    }

    private static void startFixture() throws Exception
    {
        if (endpoint != null) return;
        String existing = System.getProperty("mmm.socialEndpoint");
        if (existing != null)
        {
            checkLoopback(existing);
            endpoint = existing;
            return;
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            try (exchange)
            {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                REQUESTS.add(JsonParser.parseString(body).getAsJsonObject().get("message").getAsString());
                // Exercise submission without scheduling a synthetic incoming chat event.
                byte[] response = "{\"ok\":true,\"event\":{}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
        System.setProperty("mmm.socialEndpoint", endpoint);
        server.start();
    }

    static void run(MinecraftClient client) throws Exception
    {
        check(client.isOnThread() && client.player != null, "Chat checks require the client thread in a world");
        check(endpoint != null, "Call ChatParityChecks.start() before PublicChatClient initializes");
        String actualEndpoint = (String) field(PublicChatClient.class, "BASE_ENDPOINT").get(null);
        checkLoopback(actualEndpoint);
        check(endpoint.equals(actualEndpoint), "Chat client initialized before the loopback fixture");

        Field channel = Arrays.stream(ChatScreen.class.getDeclaredFields())
                .filter(candidate -> candidate.getName().endsWith("mmm$publicChannelSelected"))
                .findFirst().orElseThrow();
        channel.setAccessible(true);
        Field sending = field(PublicChatClient.class, "sending");
        check(!sending.getBoolean(null), "Chat checks require no in-flight submission");
        ChatHud hud = client.inGameHud.getChatHud();
        Field draft = field(ChatHud.class, "draft");
        var savedScreen = client.currentScreen;
        var savedChat = hud.toChatState();
        Object savedDraft = draft.get(hud);
        boolean savedChannel = channel.getBoolean(null);
        boolean savedEnabled = Configs.Generic.SHOW_MMM_CHAT_MESSAGES.getBooleanValue();
        String savedUuid = Configs.websiteLinkedMinecraftUuid, savedToken = Configs.websiteSyncToken;
        try
        {
            hud.getMessageHistory().clear();
            Configs.Generic.SHOW_MMM_CHAT_MESSAGES.setBooleanValue(true);
            Configs.websiteSyncToken = "isolated-chat-parity-token";
            for (int key : new int[] {257, 335})
            {
                Configs.websiteLinkedMinecraftUuid = "";
                reject(client, channel, "unlinked draft", key);
                Configs.websiteLinkedMinecraftUuid = client.player.getUuidAsString();
                reject(client, channel, "   ", key);
                reject(client, channel, "x".repeat(201), key);
                sending.setBoolean(null, true);
                try { reject(client, channel, "busy draft", key); }
                finally { sending.setBoolean(null, false); }
            }
            check(REQUESTS.isEmpty(), "Rejected drafts reached the HTTP transport");

            for (int key : new int[] {257, 335})
            {
                ChatScreen chat = openMmmChat(client, channel);
                String message = "accepted enter " + key;
                input(chat).setText("  " + message + "  ");
                List<String> expected = new ArrayList<>(hud.getMessageHistory());
                expected.add(message);
                check(chat.keyPressed(new KeyInput(key, 0, 0)), "Accepted Enter was not consumed");
                check(client.currentScreen == null, "Accepted draft did not close chat");
                check(hud.getMessageHistory().equals(expected), "Accepted draft was not added exactly once to history");
                check(draft.get(hud) == null, "Accepted message was saved as an unsent draft");
                check(!channel.getBoolean(null), "Channel did not reset after accepted send");
                awaitSubmission(sending, message);
            }

            ChatScreen chat = openMmmChat(client, channel);
            for (boolean addToHistory : new boolean[] {false, true})
            {
                String message = "direct send history " + addToHistory;
                List<String> expected = new ArrayList<>(hud.getMessageHistory());
                if (addToHistory) expected.add(message);
                chat.sendMessage(message, addToHistory);
                check(hud.getMessageHistory().equals(expected), "sendMessage ignored addToHistory");
                awaitSubmission(sending, message);
            }
            checkDisabledChannels(client, channel);
        }
        finally
        {
            client.setScreen(savedScreen);
            hud.restoreChatState(savedChat);
            draft.set(hud, savedDraft);
            channel.setBoolean(null, savedChannel);
            Configs.Generic.SHOW_MMM_CHAT_MESSAGES.setBooleanValue(savedEnabled);
            Configs.websiteLinkedMinecraftUuid = savedUuid;
            Configs.websiteSyncToken = savedToken;
            if (server != null) { server.stop(0); server = null; }
        }
    }

    private static void reject(MinecraftClient client, Field channel, String message, int key) throws Exception
    {
        ChatScreen chat = openMmmChat(client, channel);
        TextFieldWidget input = input(chat);
        input.setText(message);
        List<String> history = new ArrayList<>(client.inGameHud.getChatHud().getMessageHistory());
        check(chat.keyPressed(new KeyInput(key, 0, 0)), "Rejected Enter was not consumed");
        check(client.currentScreen == chat, "Rejected draft closed chat");
        check(input.getText().equals(message), "Rejected draft was changed or discarded");
        check(client.inGameHud.getChatHud().getMessageHistory().equals(history), "Rejected draft entered history");
    }

    private static void checkDisabledChannels(MinecraftClient client, Field channel) throws Exception
    {
        ChatScreen chat = openMmmChat(client, channel);
        check(tabLabels(client, chat).equals(List.of("MINECRAFT", "MMM")), "Enabled channel tabs were not rendered");
        Configs.Generic.SHOW_MMM_CHAT_MESSAGES.setBooleanValue(false);
        check(tabLabels(client, chat).isEmpty(), "Disabled channel tabs were rendered");
        check(!channel.getBoolean(null), "Disabling chat left the MMM channel selected");
        clickMmmTab(client, chat);
        check(!channel.getBoolean(null), "Disabled MMM tab remained clickable");

        channel.setBoolean(null, true);
        method("mmm$initializeChannel").invoke(chat, new CallbackInfo("init", false));
        check(!channel.getBoolean(null), "Disabled initialization retained the MMM channel");
        channel.setBoolean(null, true);
        var key = new CallbackInfoReturnable<Boolean>("keyPressed", true);
        method("mmm$keepRejectedDraft").invoke(chat, new KeyInput(257, 0, 0), key);
        check(!key.isCancelled() && !channel.getBoolean(null), "Disabled channel intercepted Enter");
        channel.setBoolean(null, true);
        var send = new CallbackInfo("sendMessage", true);
        // Invoke only the injected gate: do not broadcast a smoke message to a Minecraft server.
        method("mmm$sendPublicMessage").invoke(chat, "disabled channel draft", true, send);
        check(!send.isCancelled() && !channel.getBoolean(null), "Disabled channel intercepted sending");

        client.setScreen(new ChatScreen("", false));
        check(!channel.getBoolean(null), "New disabled chat selected the MMM channel");
        Configs.Generic.SHOW_MMM_CHAT_MESSAGES.setBooleanValue(true);
        chat = openMmmChat(client, channel);
        check(tabLabels(client, chat).size() == 2, "Re-enabling chat did not restore the tabs");
    }

    private static List<String> tabLabels(MinecraftClient client, ChatScreen chat) throws Exception
    {
        var state = new GuiRenderState();
        var context = new DrawContext(client, state, 0, 0);
        method("mmm$renderChannelTabs").invoke(chat, context, 0, 0, 0.0F, new CallbackInfo("render", false));
        List<String> labels = new ArrayList<>();
        state.forEachTextElement(element -> {
            StringBuilder text = new StringBuilder();
            element.orderedText.accept((index, style, codepoint) -> { text.appendCodePoint(codepoint); return true; });
            labels.add(text.toString());
        });
        return labels;
    }

    private static ChatScreen openMmmChat(MinecraftClient client, Field channel) throws Exception
    {
        Configs.Generic.SHOW_MMM_CHAT_MESSAGES.setBooleanValue(true);
        ChatScreen chat = new ChatScreen("", false);
        client.setScreen(chat);
        clickMmmTab(client, chat);
        check(channel.getBoolean(null), "MMM channel selection failed");
        return chat;
    }

    private static TextFieldWidget input(ChatScreen chat)
    {
        check(chat.getFocused() instanceof TextFieldWidget, "Chat input is not focused");
        return (TextFieldWidget) chat.getFocused();
    }

    private static void clickMmmTab(MinecraftClient client, ChatScreen chat)
    {
        TextFieldWidget input = input(chat);
        int x = input.getX() + client.textRenderer.getWidth("MINECRAFT") + 14;
        int y = Math.max(1, input.getY() - 16) + 5;
        chat.mouseClicked(new Click(x, y, new MouseInput(0, 0)), false);
    }

    private static void awaitSubmission(Field sending, String message) throws Exception
    {
        if (server != null)
            check(message.equals(REQUESTS.poll(5, TimeUnit.SECONDS)), "Loopback received the wrong message or no submission");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (sending.getBoolean(null) && System.nanoTime() < deadline) Thread.sleep(10);
        check(!sending.getBoolean(null), "Loopback submission did not complete");
        check(REQUESTS.isEmpty(), "A draft was submitted more than once");
    }

    private static void checkLoopback(String value)
    {
        URI uri = URI.create(value);
        check("http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()) && uri.getPort() > 0,
                "Chat smoke must use an explicit IPv4 loopback endpoint");
    }

    private static Field field(Class<?> type, String name) throws Exception
    {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(String suffix)
    {
        Method method = Arrays.stream(ChatScreen.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().endsWith(suffix)).findFirst().orElseThrow();
        method.setAccessible(true);
        return method;
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
