package org.node;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.breadwallet.tools.threads.executor.BRExecutor;
import com.breadwallet.tools.util.StringUtil;
import com.breadwallet.wallet.wallets.ela.WalletElaManager;
import com.google.gson.Gson;

import org.chat.lib.entity.MessageInfo;
import org.chat.lib.utils.Constants;
import org.elastos.sdk.elephantwallet.contact.Contact;
import org.elastos.sdk.elephantwallet.contact.Utils;
import org.elastos.sdk.elephantwallet.contact.internal.ContactInterface;
import org.elastos.sdk.keypair.ElastosKeypair;
import org.greenrobot.eventbus.EventBus;
import org.node.bean.MsgProtocol;

import java.util.List;

import app.elaphant.sdk.peernode.Connector;
import app.elaphant.sdk.peernode.PeerNode;
import app.elaphant.sdk.peernode.PeerNodeListener;


public class CarrierPeerNode {

    private static CarrierPeerNode mInstance;
    private static PeerNode mPeerNode;
    private static Connector mConnector = null;

    private String mPrivateKey;
    private String mPublicKey;

    private int mStartRet;

    private CarrierPeerNode(Context context) {
        if (null == mInstance) {
            init(context);
        }
    }

    public static CarrierPeerNode getInstance(Context context) {
        if (null == mInstance) {
            mInstance = new CarrierPeerNode(context);
        }
        return mInstance;
    }

    private void init(final Context context) {
        mPrivateKey = WalletElaManager.getInstance(context).getPrivateKey();
        mPublicKey = WalletElaManager.getInstance(context).getPublicKey();
        mPeerNode = PeerNode.getInstance(context.getFilesDir().getAbsolutePath(),
                Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID));
        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                mPeerNode.setListener(new PeerNodeListener.Listener() {

                    @Override
                    public byte[] onAcquire(org.elastos.sdk.elephantwallet.contact.Contact.Listener.AcquireArgs request) {
                        byte[] response = null;
                        switch (request.type) {
                            case PublicKey:
                                response = mPublicKey.getBytes();
                                break;
                            case EncryptData:
                                response = request.data;
                                break;
                            case DecryptData:
                                response = request.data;
                                break;
                            case DidPropAppId:
                                break;
                            case DidAgentAuthHeader:
                                response = getAgentAuthHeader();
                                break;
                            case SignData:
                                response = signData(request.data);
                                break;
                            default:
                                throw new RuntimeException("Unprocessed request: " + request);
                        }
                        return response;
                    }

                    @Override
                    public void onError(int errCode, String errStr, String ext) {

                    }
                });

                mStartRet = mPeerNode.start();
                createConnector("elaphantchat");
            }
        });
    }

    public void start() {
        if (mStartRet != 0) throw new RuntimeException("carrier start failed :" + mStartRet);
    }

    public void stop() {
        int ret = mPeerNode.stop();
    }

    private byte[] getAgentAuthHeader() {
        String appid = "org.elastos.debug.didplugin";
        String appkey = "b2gvzUM79yLhCbbGNWCuhSsGdqYhA7sS";
        long timestamp = System.currentTimeMillis();
        String auth = Utils.getMd5Sum(appkey + timestamp);
        String headerValue = "id=" + appid + ";time=" + timestamp + ";auth=" + auth;

        return headerValue.getBytes();
    }

    private byte[] signData(byte[] data) {
        ElastosKeypair.Data originData = new ElastosKeypair.Data();
        originData.buf = data;

        ElastosKeypair.Data signedData = new ElastosKeypair.Data();

        if (StringUtil.isNullOrEmpty(mPrivateKey)) return null;
        int signedSize = ElastosKeypair.sign(mPrivateKey, originData, originData.buf.length, signedData);
        if (signedSize <= 0) {
            return null;
        }

        return signedData.buf;
    }

    private boolean createConnector(String serviceName) {
        if (mConnector != null) return false;
        mConnector = new Connector(serviceName);
        mConnector.setMessageListener(new PeerNodeListener.MessageListener() {
            @Override
            public void onEvent(Contact.Listener.EventArgs event) {
                handleEvent(event);
            }

            @Override
            public void onReceivedMessage(String humanCode, Contact.Channel channelType, Contact.Message message) {
                handleMessage(humanCode, message);
            }
        });

        return false;
    }

    private void handleEvent(Contact.Listener.EventArgs event) {
        String text = "";
        switch (event.type) {
            case FriendRequest:
                Contact.Listener.RequestEvent requestEvent = (Contact.Listener.RequestEvent) event;
                String summary = requestEvent.summary;
                text = requestEvent.humanCode + " request friend, said: " + summary;

                Log.d("xidaokun", "CarrierPeerNode#handleEvent#FriendRequest#\ntext:"+ text);
                RequestFriendInfo requestFriendInfo = new RequestFriendInfo(requestEvent.humanCode, summary);
                postAddFriendEvent(requestFriendInfo);
                break;
            case StatusChanged:
                Contact.Listener.StatusEvent statusEvent = (Contact.Listener.StatusEvent) event;
                text = statusEvent.humanCode + " status changed " + statusEvent.status;
                Log.d("xidaokun", "CarrierPeerNode#handleEvent#StatusChanged#\ntext:"+ text);
                FriendStatusInfo friendStatusInfo = new FriendStatusInfo(statusEvent.humanCode, statusEvent.status);
                postFriendChangeEvent(friendStatusInfo);
                break;
            case HumanInfoChanged:
                Contact.Listener.InfoEvent infoEvent = (Contact.Listener.InfoEvent) event;
                text = event.humanCode + " info changed: " + infoEvent.toString();
                Log.d("xidaokun", "CarrierPeerNode#handleEvent#HumanInfoChanged#\ntext:"+ text);
                HumanChangeInfo humanChangeInfo = new HumanChangeInfo(event.humanCode, infoEvent.toString());
                postHumanInfoChangeEvent(humanChangeInfo);
                break;
            default:
                return;
        }
    }

    private void handleMessage(String humanCode, Contact.Message message) {
        MessageInfo messageInfo = new MessageInfo();

        String data = message.data.toString();
        if(StringUtil.isNullOrEmpty(data)) return;

        Log.d("xidaokun", "CarrierPeerNode#handleMessage#\nhumanCode:"+ humanCode + "\nmessage:"+data);

        MsgProtocol msgProtocol = new Gson().fromJson(data, MsgProtocol.class);
        messageInfo.setContent(msgProtocol.content);
        messageInfo.setHumanCode(humanCode);
        messageInfo.setFriendCodes(msgProtocol.friendCodes);
        messageInfo.setTime(message.timestamp);
        messageInfo.setMsgId(message.timestamp);
        messageInfo.setType(Constants.CHAT_ITEM_TYPE_LEFT);
        messageInfo.setHeader("https://xidaokun.github.io/im_boy.png");
        postMessageEvent(messageInfo);
    }

    public void createRoom(String serviceName) {
        createConnector(serviceName);
    }

    public void postAddFriendEvent(RequestFriendInfo requestFriendInfo) {
        EventBus.getDefault().post(requestFriendInfo);
    }

    public void postFriendChangeEvent(FriendStatusInfo friendStatusInfo) {
        EventBus.getDefault().post(friendStatusInfo);
    }

    public void postHumanInfoChangeEvent(HumanChangeInfo humanChangeInfo) {
        EventBus.getDefault().post(humanChangeInfo);
    }

    public void postMessageEvent(MessageInfo messageInfo) {
        EventBus.getDefault().post(messageInfo);
    }

    public void setItemInfo(Contact.HumanInfo.Item item, String value) {
        mPeerNode.setUserInfo(item, value);
    }

    public int addFriend(String friendCode, String summary) {
        return mPeerNode.addFriend(friendCode, summary);
    }

    public int acceptFriend(String friendCode) {
        return mPeerNode.acceptFriend(friendCode);
    }

    public ContactInterface.Status getFriendStatus(String friendCode) {
        return mPeerNode.getFriendStatus(friendCode);
    }

    public List<ContactInterface.FriendInfo> getFriends() {
        return mPeerNode.listFriendInfo();
    }

    public ContactInterface.UserInfo getUserInfo() {
        return mPeerNode.getUserInfo();
    }

    public int removeFriend(String friendCode) {
        return mPeerNode.removeFriend(friendCode);
    }

    public int sendMessage(String friendCode, String content) {
        ContactInterface.Message message = Contact.MakeTextMessage(content, null);
        return mConnector.sendMessage(friendCode, message);
    }

    public static class RequestFriendInfo {
        public RequestFriendInfo(String humanCode, String content) {
            this.humanCode = humanCode;
            this.content = content;
        }

        public String humanCode;
        public String content;
    }

    public static class FriendStatusInfo {
        public FriendStatusInfo(String humanCode, ContactInterface.Status status) {
            this.humanCode = humanCode;
            this.status = status;
        }

        public String humanCode;
        public ContactInterface.Status status;
    }

    public static class HumanChangeInfo {
        public HumanChangeInfo(String humanCode, String changeInfo) {
            this.humanCode = humanCode;
            this.changeInfo = changeInfo;
        }

        public String humanCode;
        public String changeInfo;
    }
}
