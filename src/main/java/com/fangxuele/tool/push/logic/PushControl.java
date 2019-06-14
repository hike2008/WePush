package com.fangxuele.tool.push.logic;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.bean.WxMaTemplateMessage;
import cn.binarywang.wx.miniapp.config.WxMaInMemoryConfig;
import cn.hutool.core.date.DateUtil;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.http.HttpClientConfig;
import com.aliyuncs.profile.DefaultProfile;
import com.fangxuele.tool.push.App;
import com.fangxuele.tool.push.dao.TPushHistoryMapper;
import com.fangxuele.tool.push.domain.TPushHistory;
import com.fangxuele.tool.push.logic.msgmaker.AliTemplateMsgMaker;
import com.fangxuele.tool.push.logic.msgmaker.AliyunMsgMaker;
import com.fangxuele.tool.push.logic.msgmaker.TxYunMsgMaker;
import com.fangxuele.tool.push.logic.msgmaker.WxKefuMsgMaker;
import com.fangxuele.tool.push.logic.msgmaker.WxMpTemplateMsgMaker;
import com.fangxuele.tool.push.logic.msgmaker.WxMaTemplateMsgMaker;
import com.fangxuele.tool.push.ui.form.MessageEditForm;
import com.fangxuele.tool.push.ui.form.PushForm;
import com.fangxuele.tool.push.ui.form.PushHisForm;
import com.fangxuele.tool.push.ui.form.SettingForm;
import com.fangxuele.tool.push.ui.form.msg.TxYunMsgForm;
import com.fangxuele.tool.push.ui.listener.MemberListener;
import com.fangxuele.tool.push.util.MybatisUtil;
import com.fangxuele.tool.push.util.SqliteUtil;
import com.fangxuele.tool.push.util.SystemUtil;
import com.github.qcloudsms.SmsSingleSender;
import com.github.qcloudsms.SmsSingleSenderResult;
import com.opencsv.CSVWriter;
import com.taobao.api.DefaultTaobaoClient;
import com.taobao.api.TaobaoClient;
import com.taobao.api.request.AlibabaAliqinFcSmsNumSendRequest;
import com.taobao.api.response.AlibabaAliqinFcSmsNumSendResponse;
import com.yunpian.sdk.YunpianClient;
import com.yunpian.sdk.model.Result;
import com.yunpian.sdk.model.SmsSingleSend;
import me.chanjar.weixin.common.util.http.apache.DefaultApacheHttpClientBuilder;
import me.chanjar.weixin.mp.api.WxMpInMemoryConfigStorage;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.bean.kefu.WxMpKefuMessage;
import me.chanjar.weixin.mp.bean.template.WxMpTemplateMessage;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <pre>
 * 推送控制
 * </pre>
 *
 * @author <a href="https://github.com/rememberber">RememBerBer</a>
 * @since 2017/6/19.
 */
public class PushControl {

    private static TPushHistoryMapper pushHistoryMapper = MybatisUtil.getSqlSession().getMapper(TPushHistoryMapper.class);

    /**
     * 模板变量前缀
     */
    public static final String TEMPLATE_VAR_PREFIX = "var";

    public volatile static WxMpService wxMpService;

    /**
     * 阿里云短信client
     */
    public volatile static IAcsClient iAcsClient;

    /**
     * 腾讯云短信sender
     */
    public volatile static SmsSingleSender smsSingleSender;

    /**
     * 阿里大于短信client
     */
    public volatile static TaobaoClient taobaoClient;

    /**
     * 云片网短信client
     */
    public volatile static YunpianClient yunpianClient;

    public volatile static WxMpInMemoryConfigStorage wxMpConfigStorage;

    public volatile static WxMaService wxMaService;

    public volatile static WxMaInMemoryConfig wxMaConfigStorage;

    /**
     * 预览消息
     *
     * @throws Exception 异常
     */
    public static boolean preview() throws Exception {
        List<String[]> msgDataList = new ArrayList<>();

        for (String data : MessageEditForm.messageEditForm.getPreviewUserField().getText().split(";")) {
            msgDataList.add(data.split(MemberListener.TXT_FILE_DATA_SEPERATOR_REGEX));
        }

        // 准备消息构造器
        prepareMsgMaker();
        switch (App.config.getMsgType()) {
            case MessageTypeEnum.MP_TEMPLATE_CODE:
                WxMpTemplateMessage wxMessageTemplate;
                WxMpService wxMpService = getWxMpService();
                WxMpTemplateMsgMaker wxMpTemplateMsgMaker = new WxMpTemplateMsgMaker();

                for (String[] msgData : msgDataList) {
                    wxMessageTemplate = wxMpTemplateMsgMaker.makeMsg(msgData);
                    wxMessageTemplate.setToUser(msgData[0].trim());
                    // ！！！发送模板消息！！！
                    wxMpService.getTemplateMsgService().sendTemplateMsg(wxMessageTemplate);
                }
                break;
            case MessageTypeEnum.MA_TEMPLATE_CODE:
                WxMaTemplateMessage wxMaMessageTemplate;
                WxMaService wxMaService = getWxMaService();
                WxMaTemplateMsgMaker wxMaTemplateMsgMaker = new WxMaTemplateMsgMaker();

                for (String[] msgData : msgDataList) {
                    wxMaMessageTemplate = wxMaTemplateMsgMaker.makeMsg(msgData);
                    wxMaMessageTemplate.setToUser(msgData[0].trim());
                    wxMaMessageTemplate.setFormId(msgData[1].trim());
                    // ！！！发送小程序模板消息！！！
                    wxMaService.getMsgService().sendTemplateMsg(wxMaMessageTemplate);
                }
                break;
            case MessageTypeEnum.KEFU_CODE:
                wxMpService = getWxMpService();
                WxMpKefuMessage wxMpKefuMessage;
                WxKefuMsgMaker wxKefuMsgMaker = new WxKefuMsgMaker();

                for (String[] msgData : msgDataList) {
                    wxMpKefuMessage = wxKefuMsgMaker.makeMsg(msgData);
                    wxMpKefuMessage.setToUser(msgData[0]);
                    // ！！！发送客服消息！！！
                    wxMpService.getKefuService().sendKefuMessage(wxMpKefuMessage);
                }
                break;
            case MessageTypeEnum.KEFU_PRIORITY_CODE:
                wxMpService = getWxMpService();
                wxKefuMsgMaker = new WxKefuMsgMaker();
                wxMpTemplateMsgMaker = new WxMpTemplateMsgMaker();

                for (String[] msgData : msgDataList) {
                    try {
                        wxMpKefuMessage = wxKefuMsgMaker.makeMsg(msgData);
                        wxMpKefuMessage.setToUser(msgData[0]);
                        // ！！！发送客服消息！！！
                        wxMpService.getKefuService().sendKefuMessage(wxMpKefuMessage);
                    } catch (Exception e) {
                        wxMessageTemplate = wxMpTemplateMsgMaker.makeMsg(msgData);
                        wxMessageTemplate.setToUser(msgData[0].trim());
                        // ！！！发送模板消息！！！
                        wxMpService.getTemplateMsgService().sendTemplateMsg(wxMessageTemplate);
                    }
                }
                break;
            case MessageTypeEnum.ALI_YUN_CODE:
                String aliyunAccessKeyId = App.config.getAliyunAccessKeyId();
                String aliyunAccessKeySecret = App.config.getAliyunAccessKeySecret();

                if (StringUtils.isEmpty(aliyunAccessKeyId) || StringUtils.isEmpty(aliyunAccessKeySecret)) {
                    JOptionPane.showMessageDialog(SettingForm.settingForm.getSettingPanel(),
                            "请先在设置中填写并保存阿里云短信相关配置！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                    return false;
                }

                IAcsClient acsClient = getAliyunIAcsClient();
                AliyunMsgMaker aliyunMsgMaker = new AliyunMsgMaker();
                for (String[] msgData : msgDataList) {
                    SendSmsRequest request = aliyunMsgMaker.makeMsg(msgData);
                    request.setPhoneNumbers(msgData[0]);
                    SendSmsResponse response = acsClient.getAcsResponse(request);

                    if (response.getCode() == null || !"OK".equals(response.getCode())) {
                        throw new Exception(response.getMessage() + ";\n\nErrorCode:" +
                                response.getCode() + ";\n\ntelNum:" + msgData[0]);
                    }
                }
                break;
            case MessageTypeEnum.TX_YUN_CODE:
                String txyunAppId = App.config.getTxyunAppId();
                String txyunAppKey = App.config.getTxyunAppKey();

                if (StringUtils.isEmpty(txyunAppId) || StringUtils.isEmpty(txyunAppKey)) {
                    JOptionPane.showMessageDialog(SettingForm.settingForm.getSettingPanel(),
                            "请先在设置中填写并保存腾讯云短信相关配置！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                    return false;
                }

                SmsSingleSender smsSingleSender = getTxYunSender();
                TxYunMsgMaker txYunMsgMaker = new TxYunMsgMaker();

                for (String[] msgData : msgDataList) {
                    String[] params = txYunMsgMaker.makeMsg(msgData);
                    SmsSingleSenderResult result = smsSingleSender.sendWithParam("86", msgData[0],
                            Integer.valueOf(TxYunMsgForm.txYunMsgForm.getMsgTemplateIdTextField().getText()),
                            params, App.config.getAliyunSign(), "", "");
                    if (result.result != 0) {
                        throw new Exception(result.toString());
                    }
                }
                break;
            case MessageTypeEnum.ALI_TEMPLATE_CODE:
                String aliServerUrl = App.config.getAliServerUrl();
                String aliAppKey = App.config.getAliAppKey();
                String aliAppSecret = App.config.getAliAppSecret();

                if (StringUtils.isEmpty(aliServerUrl) || StringUtils.isEmpty(aliAppKey)
                        || StringUtils.isEmpty(aliAppSecret)) {
                    JOptionPane.showMessageDialog(SettingForm.settingForm.getSettingPanel(),
                            "请先在设置中填写并保存阿里大于相关配置！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                    return false;
                }

                TaobaoClient client = getTaobaoClient();
                AliTemplateMsgMaker aliTemplateMsgMaker = new AliTemplateMsgMaker();
                for (String[] msgData : msgDataList) {
                    AlibabaAliqinFcSmsNumSendRequest request = aliTemplateMsgMaker.makeMsg(msgData);
                    request.setRecNum(msgData[0]);
                    AlibabaAliqinFcSmsNumSendResponse response = client.execute(request);
                    if (response.getResult() == null || !response.getResult().getSuccess()) {
                        throw new Exception(response.getBody() + ";\n\nErrorCode:" +
                                response.getErrorCode() + ";\n\ntelNum:" + msgData[0]);
                    }
                }
                break;
            case MessageTypeEnum.YUN_PIAN_CODE:
                String yunpianApiKey = App.config.getYunpianApiKey();

                if (StringUtils.isEmpty(yunpianApiKey)) {
                    JOptionPane.showMessageDialog(SettingForm.settingForm.getSettingPanel(),
                            "请先在设置中填写并保存云片网短信相关配置！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                    return false;
                }

                YunpianClient yunpianClient = getYunpianClient();

                for (String[] msgData : msgDataList) {
                    Map<String, String> params = MessageMaker.makeYunpianMessage(msgData);
                    params.put(YunpianClient.MOBILE, msgData[0]);
                    Result<SmsSingleSend> result = yunpianClient.sms().single_send(params);
                    if (result.getCode() != 0) {
                        throw new Exception(result.toString());
                    }
                }
                yunpianClient.close();
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * 微信公众号配置
     *
     * @return WxMpConfigStorage
     */
    private static WxMpInMemoryConfigStorage wxMpConfigStorage() {
        if (StringUtils.isEmpty(App.config.getWechatAppId()) || StringUtils.isEmpty(App.config.getWechatAppSecret())) {
            JOptionPane.showMessageDialog(SettingForm.settingForm.getSettingPanel(), "请先在设置中填写并保存公众号相关配置！", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            PushForm.pushForm.getScheduleRunButton().setEnabled(true);
            PushForm.pushForm.getPushStartButton().setEnabled(true);
            PushForm.pushForm.getPushStopButton().setEnabled(false);
            PushForm.pushForm.getPushTotalProgressBar().setIndeterminate(false);
            return null;
        }
        WxMpInMemoryConfigStorage configStorage = new WxMpInMemoryConfigStorage();
        configStorage.setAppId(App.config.getWechatAppId());
        configStorage.setSecret(App.config.getWechatAppSecret());
        configStorage.setToken(App.config.getWechatToken());
        configStorage.setAesKey(App.config.getWechatAesKey());
        if (App.config.isMpUseProxy()) {
            configStorage.setHttpProxyHost(App.config.getMpProxyHost());
            configStorage.setHttpProxyPort(Integer.parseInt(App.config.getMpProxyPort()));
            configStorage.setHttpProxyUsername(App.config.getMpProxyUserName());
            configStorage.setHttpProxyPassword(App.config.getMpProxyPassword());
        }
        DefaultApacheHttpClientBuilder clientBuilder = DefaultApacheHttpClientBuilder.get();
        //从连接池获取链接的超时时间(单位ms)
        clientBuilder.setConnectionRequestTimeout(10000);
        //建立链接的超时时间(单位ms)
        clientBuilder.setConnectionTimeout(5000);
        //连接池socket超时时间(单位ms)
        clientBuilder.setSoTimeout(5000);
        //空闲链接的超时时间(单位ms)
        clientBuilder.setIdleConnTimeout(60000);
        //空闲链接的检测周期(单位ms)
        clientBuilder.setCheckWaitTime(60000);
        //每路最大连接数
        clientBuilder.setMaxConnPerHost(App.config.getMaxThreadPool());
        //连接池最大连接数
        clientBuilder.setMaxTotalConn(App.config.getMaxThreadPool() * 2);
        //HttpClient请求时使用的User Agent
//        clientBuilder.setUserAgent(..)
        configStorage.setApacheHttpClientBuilder(clientBuilder);
        return configStorage;
    }

    /**
     * 微信小程序配置
     *
     * @return WxMaInMemoryConfig
     */
    private static WxMaInMemoryConfig wxMaConfigStorage() {
        WxMaInMemoryConfig configStorage = new WxMaInMemoryConfig();
        if (StringUtils.isEmpty(App.config.getMiniAppAppId()) || StringUtils.isEmpty(App.config.getMiniAppAppSecret())) {
            JOptionPane.showMessageDialog(SettingForm.settingForm.getSettingPanel(), "请先在设置中填写并保存小程序相关配置！", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            PushForm.pushForm.getScheduleRunButton().setEnabled(true);
            PushForm.pushForm.getPushStartButton().setEnabled(true);
            PushForm.pushForm.getPushStopButton().setEnabled(false);
            PushForm.pushForm.getPushTotalProgressBar().setIndeterminate(false);
            return null;
        }
        configStorage.setAppid(App.config.getMiniAppAppId());
        configStorage.setSecret(App.config.getMiniAppAppSecret());
        configStorage.setToken(App.config.getMiniAppToken());
        configStorage.setAesKey(App.config.getMiniAppAesKey());
        configStorage.setMsgDataFormat("JSON");
        if (App.config.isMaUseProxy()) {
            configStorage.setHttpProxyHost(App.config.getMaProxyHost());
            configStorage.setHttpProxyPort(Integer.parseInt(App.config.getMaProxyPort()));
            configStorage.setHttpProxyUsername(App.config.getMaProxyUserName());
            configStorage.setHttpProxyPassword(App.config.getMaProxyPassword());
        }
        DefaultApacheHttpClientBuilder clientBuilder = DefaultApacheHttpClientBuilder.get();
        //从连接池获取链接的超时时间(单位ms)
        clientBuilder.setConnectionRequestTimeout(10000);
        //建立链接的超时时间(单位ms)
        clientBuilder.setConnectionTimeout(5000);
        //连接池socket超时时间(单位ms)
        clientBuilder.setSoTimeout(5000);
        //空闲链接的超时时间(单位ms)
        clientBuilder.setIdleConnTimeout(60000);
        //空闲链接的检测周期(单位ms)
        clientBuilder.setCheckWaitTime(60000);
        //每路最大连接数
        clientBuilder.setMaxConnPerHost(App.config.getMaxThreadPool());
        //连接池最大连接数
        clientBuilder.setMaxTotalConn(App.config.getMaxThreadPool() * 2);
        //HttpClient请求时使用的User Agent
//        clientBuilder.setUserAgent(..)
        configStorage.setApacheHttpClientBuilder(clientBuilder);
        return configStorage;
    }

    /**
     * 获取微信公众号工具服务
     *
     * @return WxMpService
     */
    public static WxMpService getWxMpService() {
        if (wxMpConfigStorage == null) {
            synchronized (PushControl.class) {
                if (wxMpConfigStorage == null) {
                    wxMpConfigStorage = wxMpConfigStorage();
                }
            }
        }
        if (wxMpService == null && wxMpConfigStorage != null) {
            synchronized (PushControl.class) {
                if (wxMpService == null && wxMpConfigStorage != null) {
                    wxMpService = new WxMpServiceImpl();
                    wxMpService.setWxMpConfigStorage(wxMpConfigStorage);
                }
            }
        }
        return wxMpService;
    }

    /**
     * 获取微信小程序工具服务
     *
     * @return WxMaService
     */
    static WxMaService getWxMaService() {
        if (wxMaService == null) {
            synchronized (PushControl.class) {
                if (wxMaService == null) {
                    wxMaService = new WxMaServiceImpl();
                }
            }
        }
        if (wxMaConfigStorage == null) {
            synchronized (PushControl.class) {
                if (wxMaConfigStorage == null) {
                    wxMaConfigStorage = wxMaConfigStorage();
                    if (wxMaConfigStorage != null) {
                        wxMaService.setWxMaConfig(wxMaConfigStorage);
                    }
                }
            }
        }
        return wxMaService;
    }

    /**
     * 获取阿里云短信发送客户端
     *
     * @return IAcsClient
     */
    public static IAcsClient getAliyunIAcsClient() {
        if (iAcsClient == null) {
            synchronized (PushControl.class) {
                if (iAcsClient == null) {
                    String aliyunAccessKeyId = App.config.getAliyunAccessKeyId();
                    String aliyunAccessKeySecret = App.config.getAliyunAccessKeySecret();

                    // 创建DefaultAcsClient实例并初始化
                    DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", aliyunAccessKeyId, aliyunAccessKeySecret);

                    // 多个SDK client共享一个连接池，此处设置该连接池的参数，
                    // 比如每个host的最大连接数，超时时间等
                    HttpClientConfig clientConfig = HttpClientConfig.getDefault();
                    clientConfig.setMaxRequestsPerHost(App.config.getMaxThreadPool());
                    clientConfig.setConnectionTimeoutMillis(10000L);

                    profile.setHttpClientConfig(clientConfig);
                    iAcsClient = new DefaultAcsClient(profile);
                }
            }
        }
        return iAcsClient;
    }

    /**
     * 获取腾讯云短信发送客户端
     *
     * @return SmsSingleSender
     */
    public static SmsSingleSender getTxYunSender() {
        if (smsSingleSender == null) {
            synchronized (PushControl.class) {
                if (smsSingleSender == null) {
                    String txyunAppId = App.config.getTxyunAppId();
                    String txyunAppKey = App.config.getTxyunAppKey();

                    smsSingleSender = new SmsSingleSender(Integer.valueOf(txyunAppId), txyunAppKey);
                }
            }
        }
        return smsSingleSender;
    }

    /**
     * 获取阿里大于短信发送客户端
     *
     * @return TaobaoClient
     */
    public static TaobaoClient getTaobaoClient() {
        if (taobaoClient == null) {
            synchronized (PushControl.class) {
                if (taobaoClient == null) {
                    String aliServerUrl = App.config.getAliServerUrl();
                    String aliAppKey = App.config.getAliAppKey();
                    String aliAppSecret = App.config.getAliAppSecret();

                    taobaoClient = new DefaultTaobaoClient(aliServerUrl, aliAppKey, aliAppSecret);
                }
            }
        }
        return taobaoClient;
    }

    /**
     * 获取云片网短信发送客户端
     *
     * @return YunpianClient
     */
    public static YunpianClient getYunpianClient() {
        if (yunpianClient == null) {
            synchronized (PushControl.class) {
                if (yunpianClient == null) {
                    String yunpianApiKey = App.config.getYunpianApiKey();

                    yunpianClient = new YunpianClient(yunpianApiKey).init();
                }
            }
        }
        return yunpianClient;
    }

    /**
     * 推送停止或结束后保存数据
     */
    static void savePushData() throws IOException {
        File pushHisDir = new File(SystemUtil.configHome + "data" + File.separator + "push_his");
        if (!pushHisDir.exists()) {
            pushHisDir.mkdirs();
        }

        String msgName = MessageEditForm.messageEditForm.getMsgNameField().getText();
        String nowTime = DateUtil.now().replace(":", "_").replace(" ", "_");
        CSVWriter writer;
        int msgType = App.config.getMsgType();

        // 保存已发送
        if (PushData.sendSuccessList.size() > 0) {
            File sendSuccessFile = new File(SystemUtil.configHome + "data" +
                    File.separator + "push_his" + File.separator + MessageTypeEnum.getName(msgType) + "-" + msgName +
                    "-发送成功-" + nowTime + ".csv");
            if (!sendSuccessFile.exists()) {
                sendSuccessFile.createNewFile();
            }
            writer = new CSVWriter(new FileWriter(sendSuccessFile));

            for (String[] str : PushData.sendSuccessList) {
                writer.writeNext(str);
            }
            writer.close();

            savePushResult(msgName, "发送成功", sendSuccessFile);
            // 保存累计推送总数
            App.config.setPushTotal(App.config.getPushTotal() + PushData.sendSuccessList.size());
            App.config.save();
        }

        // 保存未发送
        for (String[] str : PushData.sendSuccessList) {
            PushData.toSendList.remove(str);
        }
        for (String[] str : PushData.sendFailList) {
            PushData.toSendList.remove(str);
        }
        if (PushData.toSendList.size() > 0) {
            File unSendFile = new File(SystemUtil.configHome + "data" + File.separator +
                    "push_his" + File.separator + MessageTypeEnum.getName(msgType) + "-" + msgName + "-未发送-" + nowTime +
                    ".csv");
            if (!unSendFile.exists()) {
                unSendFile.createNewFile();
            }
            writer = new CSVWriter(new FileWriter(unSendFile));
            for (String[] str : PushData.toSendList) {
                writer.writeNext(str);
            }
            writer.close();

            savePushResult(msgName, "未发送", unSendFile);
        }

        // 保存发送失败
        if (PushData.sendFailList.size() > 0) {
            File failSendFile = new File(SystemUtil.configHome + "data" + File.separator +
                    "push_his" + File.separator + MessageTypeEnum.getName(msgType) + "-" + msgName + "-发送失败-" + nowTime + ".csv");
            if (!failSendFile.exists()) {
                failSendFile.createNewFile();
            }
            writer = new CSVWriter(new FileWriter(failSendFile));
            for (String[] str : PushData.sendFailList) {
                writer.writeNext(str);
            }
            writer.close();

            savePushResult(msgName, "发送失败", failSendFile);
        }

        PushHisForm.init();
    }

    /**
     * 保存结果到DB
     *
     * @param msgName
     * @param resultInfo
     * @param file
     */
    private static void savePushResult(String msgName, String resultInfo, File file) {
        TPushHistory tPushHistory = new TPushHistory();
        String now = SqliteUtil.nowDateForSqlite();
        tPushHistory.setMsgType(App.config.getMsgType());
        tPushHistory.setMsgName(msgName);
        tPushHistory.setResult(resultInfo);
        tPushHistory.setCsvFile(file.getAbsolutePath());
        tPushHistory.setCreateTime(now);
        tPushHistory.setModifiedTime(now);

        pushHistoryMapper.insertSelective(tPushHistory);
    }

    /**
     * 准备消息构造器
     */
    public static void prepareMsgMaker() {
        int msgType = App.config.getMsgType();
        switch (msgType) {
            case MessageTypeEnum.MP_TEMPLATE_CODE:
                PushControl.wxMpConfigStorage = null;
                PushControl.wxMpService = null;
                WxMpTemplateMsgMaker.prepare();
                break;
            case MessageTypeEnum.MA_TEMPLATE_CODE:
                PushControl.wxMaConfigStorage = null;
                PushControl.wxMaService = null;
                WxMaTemplateMsgMaker.prepare();
                break;
            case MessageTypeEnum.KEFU_CODE:
                PushControl.wxMpConfigStorage = null;
                PushControl.wxMpService = null;
                WxKefuMsgMaker.prepare();
                break;
            case MessageTypeEnum.KEFU_PRIORITY_CODE:
                PushControl.wxMpConfigStorage = null;
                PushControl.wxMpService = null;
                WxKefuMsgMaker.prepare();
                WxMpTemplateMsgMaker.prepare();
                break;
            case MessageTypeEnum.ALI_YUN_CODE:
                AliyunMsgMaker.prepare();
                break;
            case MessageTypeEnum.ALI_TEMPLATE_CODE:
                AliTemplateMsgMaker.prepare();
                break;
            case MessageTypeEnum.TX_YUN_CODE:
                TxYunMsgMaker.prepare();
                break;
            default:
        }
    }

}