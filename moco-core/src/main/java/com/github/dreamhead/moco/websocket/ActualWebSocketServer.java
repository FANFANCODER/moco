package com.github.dreamhead.moco.websocket;

import com.github.dreamhead.moco.MocoConfig;
import com.github.dreamhead.moco.RequestMatcher;
import com.github.dreamhead.moco.WebSocketServer;
import com.github.dreamhead.moco.internal.BaseActualServer;
import com.github.dreamhead.moco.internal.SessionContext;
import com.github.dreamhead.moco.model.MessageContent;
import com.github.dreamhead.moco.monitor.QuietMonitor;
import com.github.dreamhead.moco.resource.Resource;
import com.github.dreamhead.moco.setting.Setting;
import com.google.common.collect.ImmutableList;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.concurrent.GlobalEventExecutor;

public final class ActualWebSocketServer
        extends BaseActualServer<WebsocketResponseSetting, ActualWebSocketServer>
        implements WebSocketServer {
    private Resource connected;
    private ChannelGroup group;
    private String uri;

    public ActualWebSocketServer(final String uri) {
        super(0, new QuietMonitor(), new MocoConfig[0]);
        this.uri = uri;
        this.group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    }

    public final void connected(final Resource resource) {
        this.connected = resource;
    }

    private void connect(final Channel channel) {
        this.group.add(channel);
    }

    public void disconnect(final Channel channel) {
        this.group.remove(channel);
    }

    public String getUri() {
        return uri;
    }

    private void sendConnected(final Channel channel) {
        if (connected != null) {
            MessageContent messageContent = this.connected.readFor(null);
            channel.writeAndFlush(new TextWebSocketFrame(messageContent.toString()));
        }
    }

    public void connectRequest(final ChannelHandlerContext ctx, final FullHttpRequest request) {
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                getUri(), null, false);
        WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(request);
        Channel channel = ctx.channel();
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(channel);
            return;
        }

        handshaker.handshake(channel, request);
        connect(channel);
        sendConnected(channel);
    }

    @Override
    protected Setting<WebsocketResponseSetting> newSetting(final RequestMatcher matcher) {
        return new WebsocketSetting(matcher);
    }

    @Override
    protected ActualWebSocketServer createMergeServer(final ActualWebSocketServer thatServer) {
        return new ActualWebSocketServer(this.uri);
    }

    @Override
    protected WebsocketResponseSetting onRequestAttached(final RequestMatcher matcher) {
        WebsocketSetting baseSetting = new WebsocketSetting(matcher);
        addSetting(baseSetting);
        return baseSetting;
    }

    public WebsocketResponse handleRequest(final ChannelHandlerContext ctx, final TextWebSocketFrame message) {
        DefaultWebsocketRequest request = new DefaultWebsocketRequest(message);
        DefaultWebsocketResponse response = new DefaultWebsocketResponse();
        SessionContext context = new SessionContext(request, response);
        return (WebsocketResponse)this.getResponse(context).orElseThrow(IllegalArgumentException::new);
    }
}
