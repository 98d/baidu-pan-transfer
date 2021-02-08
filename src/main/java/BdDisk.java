import cn.hutool.core.io.FileUtil;
import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.core.text.UnicodeUtil;
import cn.hutool.http.HttpUtil;
import entity.BdFileDetail;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import okhttp3.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author by seinonana
 * @version 1.0
 * @classname BdDisk
 * @date 2021/2/5 17:11
 */
@Slf4j
public class BdDisk {
    private static final Map<String, String> mapHeaders;
    private static final String DIR = "1";
    public static final String ERRNO_SUCCESS = "0";
    public static final String ERRNO = "errno";
    private static Headers headers;
    private static final OkHttpClient okHttpClient;
    private static final Pattern bdstokenPattern = Pattern.compile("\"bdstoken\":\"(\\S+?)\"");
    private static final Pattern shareidPattern = Pattern.compile("\"shareid\":(\\d+?),");
    private static final Pattern pathPattern = Pattern.compile("\"path\":(\\S+?),");
    private static final Pattern ukPattern = Pattern.compile("\"uk\":(\\d+?),");
    private static final Pattern fsidPattern = Pattern.compile("\"fs_id\":(\\d+?),");
    private static final String bdclndRegex = "(?<=BDCLND=)(\\S+?)(?=;)";
    private static String bdstoken;
    private static final List<BdFileDetail> bdFileDetails;
    private static final LinkedList<String> dirLIST;

    static {
        mapHeaders = new HashMap<>();
        mapHeaders.put("Host", "pan.baidu.com");
        mapHeaders.put("Connection", "keep-alive");
        mapHeaders.put("Upgrade-Insecure-Requests", "1");
        mapHeaders.put("Sec-Fetch-Dest", "empty");
        mapHeaders.put("Origin", "document");
        mapHeaders.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        mapHeaders.put("Sec-Fetch-Site", "same-origin");
        mapHeaders.put("Sec-Fetch-Mode", "cors");
        mapHeaders.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.146 Safari/537.36");
        mapHeaders.put("Accept-Language", "zh-CN,zh;q=0.9");
        mapHeaders.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        bdFileDetails = new LinkedList<>();
        dirLIST = new LinkedList<>();
        okHttpClient = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).build();
    }


    public static void main(String[] args) throws Exception {
        String cookie = null;
        String linkUrl = "https://pan.baidu.com/s/1juJTuzws8LhVbuMtNMTGkg";
        String passCode = "Az04";
        String folder = "/test1";
        long start = System.currentTimeMillis();
        startTransferFiles(cookie, linkUrl, passCode, folder);
        long end = System.currentTimeMillis();
        log.warn("转存完成,耗时" + (end - start));
    }


    /**
     * 开始递归转存所有文件
     *
     * @param [cookie, linkUrl, passCode]
     * @param folder   保存目标文件夹
     * @return void
     * @author seinonana
     * @date 2021/2/8 9:31
     */
    public static boolean startTransferFiles(String cookie, String linkUrl, String passCode, String folder) {
        try {
            mapHeaders.put("Cookie", cookie);
            headers = Headers.of(mapHeaders);
            getBdstoken();
            checkLinks(linkUrl, passCode, "", "");
            LinkedList<String> pathList = new LinkedList<>();
            String[] res = new String[3];
            executeLinkUrl(linkUrl, res, pathList);
            loopSaveFile(linkUrl, pathList, res, folder);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("转存分享链接失败");
            return false;
        }
        log.warn("转存成功");
        return true;


    }

    private static void loopSaveFile(String linkUrl, LinkedList<String> pathList, String[] res, String folder) throws InterruptedException, IOException {
        String path = pathList.get(0).replaceFirst("\\\\", "");
        dirLIST.add(UnicodeUtil.toString(path));
        while (!dirLIST.isEmpty()) {
            Thread.sleep(2000);
            transferLinkUrl(linkUrl, dirLIST.poll(), res, folder);
        }
    }


    /**
     * 转存文件
     *
     * @param [sharedId, userid, fsid, dirName, linkUrl]
     * @return java.lang.String
     * @author seinonana
     * @date 2021/2/8 9:01
     */
    private static String transferFiles(String sharedId, String userid, String fsid, String dirName, String linkUrl) throws IOException {
        String url = UrlBuilder.ofHttpWithoutEncode("https://pan.baidu.com/share/transfer")
                .addQuery("shareid", sharedId)
                .addQuery("from", userid)
                .addQuery("ondup", "newcopy")
                .addQuery("async", "1")
                .addQuery("channel", "chunlei")
                .addQuery("web", "1")
                .addQuery("bdstoken", bdstoken)
                .addQuery("clienttype", "0")
                .build();
        FormBody formBody = new FormBody.Builder()
                .add("fsidlist", "[" + fsid + "]")
                .add("path", dirName)
                .build();
        mapHeaders.put("Referer", linkUrl);
        return executePost(Headers.of(mapHeaders), url, formBody);
    }

    private static String getSubstr(String str) {
        int i = str.lastIndexOf(":");
        return str.substring(i + 1, str.length() - 1);
    }

    /**
     * 创建文件夹
     *
     * @param [dirName] 文件夹名字
     * @return okhttp3.Response
     * @author seinonana
     * @date 2021/2/8 8:58
     */
    private static String makeDir(String dirName) throws IOException {
        String url = UrlBuilder.ofHttpWithoutEncode("https://pan.baidu.com/api/create")
                .addQuery("a", "commit")
                .addQuery("bdstoken", bdstoken).build();
        FormBody formBody = new FormBody.Builder()
                .add("path", dirName)
                .add("isdir", "1")
                .add("block_list", "[]")
                .build();
        return executePost(headers, url, formBody);
    }

    private static String executePost(Headers header, String url, FormBody formBody) throws IOException {
        Request request = new Request.Builder()
                .headers(header)
                .post(formBody)
                .url(url)
                .build();
        Response response = okHttpClient.newCall(request).execute();
        String resStr = response.body().string();
        response.body().close();
        return resStr;
    }

    /**
     * 获取bdstoken
     *
     * @param []
     * @return java.lang.String
     * @author seinonana
     * @date 2021/2/8 9:07
     */
    private static String getBdstoken() throws IOException {
        String getBdsTokenUrl = "https://pan.baidu.com/disk/home";
        String strRes = executeGet(getBdsTokenUrl);
        Matcher matcher = bdstokenPattern.matcher(strRes);
        if (matcher.find()) {
            bdstoken = matcher.group().replaceAll("\"bdstoken\":\"", "").replaceAll("\"", "");
            return bdstoken;
        } else {
            return null;
        }
    }


    /**
     * 获取目录列表
     *
     * @param [dir]
     * @return java.lang.String
     * @author seinonana
     * @date 2021/2/8 9:07
     */
    private static String getDirList(String dir) throws IOException {
        String url = UrlBuilder.ofHttpWithoutEncode("https://pan.baidu.com/api/list")
                .addQuery("order", "time")
                .addQuery("desc", "1")
                .addQuery("showempty", "0")
                .addQuery("web", "1")
                .addQuery("page", "1")
                .addQuery("num", "1000")
                .addQuery("dir", URLEncoder.encode(dir, "UTF-8"))
                .addQuery("bdstoken", bdstoken)
                .build();
        return executeGet(url);
    }

    /**
     * 校验分享链接地址，设置请求头
     *
     * @param [linkUrl, passCode]
     * @return void
     * @author seinonana
     * @date 2021/2/8 9:26
     */
    private static void checkLinks(String linkUrl, String passCode, String vcode, String vocode_str) throws IOException {
        String surl = linkUrl.substring(25);
        String url = UrlBuilder.ofHttpWithoutEncode("https://pan.baidu.com/share/verify")
                .addQuery("surl", surl)
                .addQuery("bdstoken", bdstoken)
                .build();
        FormBody formBody = new FormBody.Builder()
                .add("pwd", passCode)
                .add("vcode", vcode)
                .add("vcode_str", vocode_str).build();
        Headers header = BdDisk.headers.newBuilder()
                .set("Referer", linkUrl)
                .set("Origin", "https://pan.baidu.com")
                .build();
        String stringRes = executePost(header, url, formBody);
        JSONObject jsonObject = JSONObject.fromObject(stringRes);
        String ERRNO_RES = jsonObject.getString(ERRNO);
        if (ERRNO_SUCCESS.equals(ERRNO_RES)) {
            String randsk = jsonObject.getString("randsk");
            mapHeaders.compute("Cookie", (k, v) -> v.replaceAll(bdclndRegex, randsk));
            headers = Headers.of(mapHeaders);
        } else if ("-62".equals(ERRNO_RES)) {
           getVcode(linkUrl,passCode);
        }
        log.warn(stringRes);
        log.warn("1");
    }

    /** 需要输入验证码提取文件
     * @author seinonana
     * @date 2021/2/8 11:44
     * @param [linkUrl, passCode]
     * @return void
     */
    public static void getVcode(String linkUrl, String passCode) throws IOException {
        String vcodeUrl = UrlBuilder.ofHttpWithoutEncode("https://pan.baidu.com/api/getcaptcha")
                .addQuery("prod", "shareverify")
                .addQuery("web", "1")
                .addQuery("channel", "chunlei")
                .addQuery("bdstoken", bdstoken)
                .addQuery("app_id", "250528")
                .build();
        String vocodeRes = executeGet(vcodeUrl);
        JSONObject vcodeJsonObject = JSONObject.fromObject(vocodeRes);
        if (ERRNO_SUCCESS.equals(vcodeJsonObject.getString(ERRNO))) {
            String vcode_img = vcodeJsonObject.getString("vcode_img");
            String vcodeStrFromObject = vcodeJsonObject.getString("vcode_str");
            String vcodeStr = null;
            String tmpFile = System.getProperty("java.io.tmpdir")+"/vcode.jpg";
            System.out.println(tmpFile);
            long size = HttpUtil.downloadFile(vcode_img, FileUtil.file(tmpFile));
            if (size > 0){
                log.info("下载验证码成功");
                Runtime runtime = Runtime.getRuntime();
                runtime.exec("cmd /c " + tmpFile);
                Scanner scan = new Scanner(System.in);
                // 从键盘接收数据
                // nextLine方式接收字符串
                System.out.println("请输入验证码：");
                // 判断是否还有输入
                if (scan.hasNextLine()) {
                    vcodeStr = scan.nextLine();
                    System.out.println("输入的数据为：" + vcodeStr);
                }
                scan.close();
                checkLinks(linkUrl, passCode, vcodeStr, vcodeStrFromObject);
            }else {
                log.error("下载验证码失败");
            }
        }
    }
    /**
     * 进入分享链接
     *
     * @param [linkUrl, pathList]
     * @return java.lang.String[]
     * @author seinonana
     * @date 2021/2/8 9:16
     */
    private static String executeLinkUrl(String linkUrl, String[] strs, List<String> pathList) throws IOException {
        String res = executeGet(linkUrl);
        strs[0] = getMatchPattern(res, shareidPattern);
        strs[1] = getMatchPattern(res, ukPattern);
        strs[2] = getMatchPattern(res, fsidPattern);
        addPathFromLinkRes(pathList, res);
        return res;

    }

    /**
     * 添加分享链接的文件夹路径
     *
     * @param [pathList, res]
     * @return void
     * @author seinonana
     * @date 2021/2/8 9:25
     */
    private static void addPathFromLinkRes(List<String> pathList, String res) {
        Matcher matcher3 = pathPattern.matcher(res);
        while (matcher3.find()) {
            String substr = getSubstr(matcher3.group());
            String pathMatch = substr.replaceAll("\"", "");
            pathList.add(pathMatch);
        }
    }


    /**
     * 正则匹配查询结果
     *
     * @param [res, pattern]
     * @return java.lang.String
     * @author seinonana
     * @date 2021/2/8 9:20
     */
    private static String getMatchPattern(String res, Pattern pattern) {
        String matchRes = null;
        Matcher matcher = pattern.matcher(res);
        if (matcher.find()) {
            matchRes = getSubstr(matcher.group());
        }
        return matchRes;
    }

    /**
     * 转存文件，并将转存失败的加入队列中
     *
     * @param [linkUrl, dirName, params, folder]
     * @return void
     * @author seinonana
     * @date 2021/2/8 10:06
     */
    private static void transferLinkUrl(String linkUrl, String dirName, String[] params, String folder) throws IOException, InterruptedException {
        String url = getListFileUrl(dirName, params);
        String stringRes = executeGet(url);
        JSONObject jsonObject = JSONObject.fromObject(stringRes);
        if (ERRNO_SUCCESS.equals(jsonObject.getString(ERRNO))){
            JSONArray list = jsonObject.getJSONArray("list");
            for (int i = 0; i < list.size(); i++) {
                JSONObject jsonObject1 = list.getJSONObject(i);
                String isdir = jsonObject1.getString("isdir");
                String path = jsonObject1.getString("path");
                String fs_id = jsonObject1.getString("fs_id");
                String targetPath = folder + path;
                String parentPath = targetPath.substring(0, targetPath.lastIndexOf("/"));
                //查看我的百度网盘中目录是否存在，不存在则创建目录
                checkPathExist(parentPath);
                // 先执行转存，文件夹转存失败后则进入后逐个保存
                String res = transferFiles(params[0], params[1], fs_id, parentPath, linkUrl);
                if (DIR.equals(isdir)) {
                    JSONObject jsonObject2 = JSONObject.fromObject(res);
                    if (ERRNO_SUCCESS.equals(jsonObject2.getString(ERRNO))) {
                        log.warn("保存成功文件夾" + path);
                    } else {
                        dirLIST.add(path);
                    }
                } else {
                    log.warn("保存文件结果：" + res);
                }
            }
        }else {
            log.warn("保存出错："+UnicodeUtil.toString(stringRes));
        }

    }

    /**
     * 查看我的网盘中是否存在目标文件夹，不存在则创建该文件夹
     *
     * @param [parentPath]
     * @return void
     * @author seinonana
     * @date 2021/2/8 10:07
     */
    private static void checkPathExist(String parentPath) throws IOException {
        String dirList = getDirList(parentPath);
        JSONObject dirListJsonObject = JSONObject.fromObject(dirList);
        int errno = dirListJsonObject.getInt("errno");
        if (errno != 0) {
            makeDir(parentPath);
        }
    }

    /**
     * 获取查看分享链接文件信息的URL
     *
     * @param [dirName, params]
     * @return java.lang.String
     * @author seinonana
     * @date 2021/2/8 9:52
     */
    private static String getListFileUrl(String dirName, String[] params) {
        String url = UrlBuilder.ofHttpWithoutEncode("https://pan.baidu.com/share/list")
                .addQuery("uk", params[1])
                .addQuery("shareid", params[0])
                .addQuery("order", "other")
                .addQuery("desc", "1")
                .addQuery("showempty", "0")
                .addQuery("web", "1")
                .addQuery("page", "1")
                .addQuery("num", "100")
                .addQuery("dir", dirName)
                .addQuery("channel", "chunlei")
                .addQuery("bdstoken", bdstoken)
                .addQuery("clienttype", "0")
                .addQuery("app_id", "250528").build();
        //        .addQuery("logid", "Q0VDNkFERkZCRTFDREIzNTFFRjk0Q0U4QjQxQzYzNTI6Rkc9MQ==")

        return url;
    }

    private static String executeGet(String url) {
        Request request = new Request.Builder()
                .headers(headers)
                .url(url)
                .get()
                .build();
        Response response = null;
        try {
            response = okHttpClient.newCall(request).execute();
            String string = response.body().string();
            response.body().close();
            return string;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
