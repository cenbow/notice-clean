package com.silita.biaodaa.rules.ruleImpl.hunan;

import com.silita.biaodaa.common.Constant;
import com.silita.biaodaa.common.elastic.indexes.IdxZhaobiaoSnatch;
import com.silita.biaodaa.common.elastic.indexes.IdxZhongbiaoSnatch;
import com.silita.biaodaa.common.redis.RedisClear;
import com.silita.biaodaa.dao_temp.SnatchNoticeHuNanDao;
import com.silita.biaodaa.disruptor.DisruptorOperator;
import com.silita.biaodaa.service.SnatchService;
import com.silita.biaodaa.utils.CNNumberFormat;
import com.silita.biaodaa.utils.ChineseCompressUtil;
import com.silita.biaodaa.utils.MyStringUtils;
import com.silita.biaodaa.utils.RouteUtils;
import com.snatch.model.AnalyzeDetail;
import com.snatch.model.AnalyzeDetailZhongBiao;
import com.snatch.model.EsNotice;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by dh on 2018/3/14.
 */
public abstract class HunanBaseRule {

    private Logger logger = Logger.getLogger(HunanBaseRule.class);

    @Autowired
    private DisruptorOperator disruptorOperator;

    @Autowired
    protected RedisClear redisClear;

    @Autowired
    protected SnatchService snatchService;

    @Autowired
    protected SnatchNoticeHuNanDao snatchNoticeHuNanDao;


    protected ChineseCompressUtil chineseCompressUtil = new ChineseCompressUtil();

    protected static String[] normalUrl = {"www.zjjsggzy.gov.cn","www.sysggzy.com","ggzyjy.xxz.gov.cn","ggzy.xiangtan.gov.cn","csggzy.gov.cn"};

    protected static String[] keyWords1 = {"项目","施工","工程","标段","监理","代理","采购","勘察","设计"};

    protected static String[] keyWords2 = {"控制价","修改","终止","废标","开标","变更","更正","调整","延期","推迟","延长",
            "澄清","流标","答疑","补疑","质疑","补充","补遗","暂停","入围", "资格预审","资审结果",
            "合同","结果","成交","成果","中选","比选","预审","谈判","磋商","询价","竞价","单一来源"};

    protected static String[] keyWords3 = {"监理","代理","采购","勘察","设计"};

    protected static String[] keyWords4 = {"中标","修改","终止","废标","开标","变更","更正","调整","延期","推迟","延长",
            "澄清","流标","答疑","补疑","质疑","补充","补遗","暂停"};

    protected static String[] keyWords5 = {"招标","中标","修改","终止","废标","开标","变更","更正","调整","延期","推迟","延长","澄清","流标","答疑",
            "补疑","质疑","补充","补遗","暂停"};

    protected static String[] keyWords6 = {"招标","中标","修改","终止","废标","开标","变更","更正","调整","延期","推迟","延长","澄清",
            "流标","答疑","补疑","质疑","补充","补遗","暂停","通知","名称","其他","编号"};

    protected static String[] keyWords7 = {"项目","施工","工程","标段","监理","代理","采购","勘察","设计","招标","中标","控制价","修改",
            "终止","废标","开标","变更","更正","调整","延期","推迟","延长","澄清","流标","答疑","补疑","质疑","补充","补遗","暂停",
            "入围","资格预审","资审结果","合同","结果","成交","成果","中选","比选","预审","谈判","磋商","询价","竞价","单一来源",
            "公告","公示","关于","信息公示表","回复","候选人","信息"};

    protected String[] mainWebside = {"ggzyjy.xxz.gov.cn", "ggzy.huaihua.gov.cn", "ggzy.yzcity.gov.cn", "czggzy.czs.gov.cn", "ggzy.yueyang.gov.cn", "sysggzy.com", "ggzy.xiangtan.gov.cn", "hyggzyjy.hengyang.gov.cn", "zzzyjy.cn", "ggzy.changde.gov.cn", "csx.gov.cn", "liuyang.gov.cn", "wangcheng.gov.cn", "61.186.94.156", "csggzy.gov.cn", "zjjsggzy.gov.cn", "ldggzy.hnloudi.gov.cn", "bidding.hunan.gov.cn", "jyzx.yiyang.gov.cn"};

    public String clearKeyWord (String str) {
        for (int i = 0; i < keyWords7.length; i++) {
            if (str.contains(keyWords7[i])) {
                StringBuilder sb = new StringBuilder(str);
                int a = str.indexOf(keyWords7[i]);
                sb = sb.delete(a,a + keyWords7[i].length());
                str = sb.toString();
            }
        }
        return str;
    }

    /**
     * 判断公告是否为张家界、长沙、邵阳、湘西、湘潭
     * @param noticeUrl
     * @return
     */
    public int urlIndexOf (String noticeUrl) {
        for (int i = 0; i < normalUrl.length; i++) {
            if (noticeUrl.contains(normalUrl[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 替换标题中的空格与符号，标题前后添加%
     * @param title
     * @return
     */
    public String replaceStrSymbol (String title) {
        if(MyStringUtils.isNotNull(title)){
            // 标题中的空格符号替换为%
            title = title.replaceAll("[\\s~·`!！@￥$%^……&*（()）\\-——\\-_=+【\\[\\]】｛{}｝\\|、\\\\；;：:‘'“”\"，,《<。.》>、/？? ]","%");

            // 替换相关关键字为%
            title = title.replaceAll("(招标|中标|项目|施工|工程)","%");

            // 标题前后添加%
            if (title.charAt(0) != '%') {
                title = "%" + title;
            }
            if (title.charAt(title.length() - 1) != '%') {
                title = title + "%";
            }
        }
        return title;
    }

    /**
     * 判断字符串的所有字符是否都一样
     * @param str
     * @return
     */
    public boolean allIsSameChars (String str) {
        if(str!=null && str.length()>1) {
            char a = str.charAt(0);
            for (int i = 1; i < str.length(); i++) {
                if (str.charAt(i) != a) {
                    return false;
                }
            }
            return true;
        }else{
            return true;
        }
    }



    /**
     * 标题关键字截取
     * @param title
     * @return
     */
    public String subSearchTitle(String title) {
        if (title.contains("关于") && title.indexOf("关于") == 0) {
            title = title.substring(2);
        }

        // 一级关键字截取
        String[] rank1KeyWord = new String[3];
        System.arraycopy(keyWords1,0,rank1KeyWord,0,rank1KeyWord.length);
        int rank1KeyWordIndex = keyWordsIndex(title,rank1KeyWord);
        if (rank1KeyWordIndex != -1) {
            return title.substring(0,rank1KeyWordIndex);
        }

        // 二级关键字截取
        String[] rank2KeyWord = new String[6];
        System.arraycopy(keyWords1,3,rank2KeyWord,0,rank2KeyWord.length);
        int rank2KeyWordIndex = keyWordsIndex(title,rank2KeyWord);
        if (rank2KeyWordIndex != -1) {
            return title.substring(0,rank2KeyWordIndex);
        }

        // 三级关键字截取
        String[] rank3KeyWord = {"招标","中标"};
        int rank3KeyWordIndex = keyWordsIndex(title,rank3KeyWord);
        if (rank3KeyWordIndex != -1) {
            return title.substring(0,rank3KeyWordIndex);
        }

        // 四级关键字截取
        int rank4KeyWordIndex = keyWordsIndex(title,keyWords2);
        if (rank4KeyWordIndex != -1 && !title.contains("维修改造")) {
            return title.substring(0,rank4KeyWordIndex);
        }

        // 截取 50% 的字符
        return subHalfString(title);
    }

    /**
     * 截取中间一半的字符串
     * @param str
     * @return
     */
    public String subHalfString (String str) {
        int a = str.length() / 2;
        int b = a / 2;
        str = str.substring(b);
        str = str.substring(0,str.length() - (a - b));
        return str;
    }

    /**
     * 按照标题顺序，返回第一个关键字的下标
     * @param str
     * @param keyWords 关键字数组
     * @return
     */
    public int keyWordsIndex (String str , String[] keyWords) {
        List<Integer> indexs = new ArrayList<Integer>();
        for (int i = 0; i < keyWords.length; i++) {
            int index = str.indexOf(keyWords[i]);
            if (index != -1) {
                indexs.add(index);
            }
        }

        if (indexs.isEmpty()) {
            return -1;
        }

        // 排序
        Integer[] b =  indexs.toArray(new Integer[0]);
        Arrays.sort(b);
        return b[0];
    }

    /**
     * 新进公告属性设置（入库时需要）
     * @param notice
     * @return
     */
    public EsNotice setNoticeAttribute(EsNotice notice) {
        if (StringUtils.isBlank(notice.getSnatchNumber())) {
            notice.setSnatchNumber("");
        }
        String businessType = notice.getBusinessType();
        if (StringUtils.isBlank(businessType)) {
            notice.setBusinessType("");
        }

        if (StringUtils.isNotBlank(businessType) && businessType.equals("0")) {
            businessType = "3";
        }else {
            //判断公告是什么类型
            if (notice.getTitle().indexOf("设计") != -1) {
                businessType = "1";
            } else if (notice.getTitle().indexOf("监理") != -1) {
                businessType = "2";
            } else if (notice.getTitle().indexOf("采购") != -1 || notice.getTitle().indexOf("谈判") != -1 || notice.getTitle().indexOf("磋商") != -1) {
                businessType = "3";
            } else if (notice.getTitle().indexOf("勘察") != -1) {
                businessType = "4";
            } else if (notice.getTitle().indexOf("检测") != -1) {
                businessType = "5";
            } else {
                businessType = "0";
            }
        }
        notice.setBiddingType(businessType);
        // type属性分离为2个字段（type otherType）
        int type = notice.getType();
        int otherType = 0;
        if (type < 10) {
            if (notice.getTitle().indexOf("补充") != -1) {
                otherType = 1;
            } else if (notice.getTitle().indexOf("答疑") != -1) {
                otherType = 2;
            } else if (notice.getTitle().indexOf("流标") != -1) {
                otherType = 3;
            } else if (notice.getTitle().indexOf("澄清") != -1) {
                otherType = 4;
            } else if (notice.getTitle().indexOf("延期") != -1) {
                otherType = 5;
            } else if (notice.getTitle().indexOf("更正公告") != -1) {
                otherType = 6;
            } else if (notice.getTitle().indexOf("废标") != -1 && notice.getTitle().indexOf("终止") != -1) {
                otherType = 7;
            } else if (notice.getTitle().indexOf("终止") != -1) {
                otherType = 8;
            }
        } else if (type == 11) {
            otherType = 1;
        } else if (type == 12) {
            otherType = 2;
        } else if (type == 13) {
            otherType = 3;
        } else if (type == 14) {
            otherType = 4;
        } else if (type == 15) {
            otherType = 5;
        } else if (type == 16) {
            otherType = 6;
        } else if (type == 17) {
            otherType = 7;
        } else if (type == 18) {
            otherType = 8;
        } else if (type == 19) {
            otherType = 9;
        } else if (type == 20) {
            otherType = 10;
        } else if (type == 21) {
            otherType = 11;
        } else if (type == 22) {
            otherType = 12;
        } else if (type == 23) {
            otherType = 13;
        } else if (type == 24) {
            otherType = 14;
        } else {
            otherType = type;
        }
        if (type == 2 || type == 5 || type == 51 || type == 52) {
            type = 2;
        } else {
            type = 0;
        }

        if (notice.getTitle().endsWith("信息公示表")) {
            type = 2;
        }

        notice.setOtherType(String.valueOf(otherType));
        notice.setType(type);

        String areaRank = notice.getAreaRank();
        if (StringUtils.isBlank(areaRank) || areaRank.equals("___")) {
            //查询湖南的抓取的网站和当前url做对比。获取网站等级
            List<Map<String, Object>> webList = snatchService.querysWebSitePlan(notice.getTableName().replaceAll("mishu.", ""));
            Integer rank = 0;
            for (Map<String, Object> wm : webList) {
                if (notice.getUrl().indexOf(String.valueOf(wm.get("url"))) > -1) {
                    rank = Integer.valueOf(String.valueOf(wm.get("rank")));
                    notice.setWebsitePlanId(Integer.valueOf(String.valueOf(wm.get("id"))));
                    break;
                }
            }
            notice.setRank(rank);
        } else {
            notice.setRank(Integer.parseInt(areaRank));
        }
        if (notice.getWebsitePlanId() == null) {
            notice.setWebsitePlanId(0);
        }
        if (notice.getType() == 2) {
            AnalyzeDetailZhongBiao detailZhongBiao = notice.getDetailZhongBiao();
            if (detailZhongBiao == null){
                detailZhongBiao = new AnalyzeDetailZhongBiao();
            }
            detailZhongBiao.setGsDate(notice.getOpenDate());
            notice.setDetailZhongBiao(detailZhongBiao);
        } else {
            AnalyzeDetail detail = notice.getDetail();
            if (detail == null) {
                detail = new AnalyzeDetail();
            }
            detail.setGsDate(notice.getOpenDate());
            notice.setDetail(detail);
        }
        return notice;
    }

    /**
     * 招标、中标公告入库(无重复公告)
     * @param notice
     */
    public void handleNotRepeat(EsNotice notice) {
        setNoticeAttribute(notice);
        Map<String, String> map = snatchNoticeHuNanDao.insertNotice(notice);//插入公告内容以及url表

        String uuid = map.get("id");
        String otherType = map.get("otherType");
        String biddingType = map.get("biddingType");

        notice.setOtherType(otherType);
        notice.setBiddingType(biddingType);
        notice.setUuid(uuid);

        String source = notice.getSource();
        //仅湖南数据更新维度，资质与es
        if(source.equals(Constant.HUNAN_SOURCE)) {
            // 插入维度表
            if (notice.getType() == 2) {
                insertZhongbiaoAnalyzeDetail(notice.getDetailZhongBiao(), notice);
            } else {
                insertZhaobiaoAnalyzeDetail(notice.getDetail(), notice);
            }

            if (notice.getType() == 2) { //中标直接更新索引，不涉及资质
                try {
                    logger.info("中标公告插入es：start");
                    snatchNoticeHuNanDao.insertZhongbiaoEsNotice(notice);
                    logger.info("中标公告插入es: finished");
                } catch (Exception e) {
                    logger.error("@@@@ES中标入库报错" + e);
                }
            } else {
                //非2中标公告发起资质匹配任务
                disruptorOperator.publishQuaParse(notice);
            }
        }
    }

    /**
     * 解析结果插入招标公告临时维度表
     * @param zhaobiaoDetail
     * @param notice
     */
    public void insertZhaobiaoAnalyzeDetail(AnalyzeDetail zhaobiaoDetail, EsNotice notice) {
        //把解析结果插入维度临时表
        zhaobiaoDetail.setRedisId(Integer.parseInt(notice.getUuid()));
        snatchNoticeHuNanDao.insertOrUpdateAnalyzeDetail(zhaobiaoDetail);
    }


    /**
     * 解析结果插入中标公告临时维度表
     * @param zhongbiaoDetail
     * @param notice
     */
    public void insertZhongbiaoAnalyzeDetail(AnalyzeDetailZhongBiao zhongbiaoDetail, EsNotice notice) {
        //把解析结果插入程序解析维度表
        zhongbiaoDetail.setRedisId(Integer.parseInt(notice.getUuid()));
        snatchNoticeHuNanDao.insertOrUpdateAnalyzeDetailZhongBiao(zhongbiaoDetail);
    }

    /**
     * 判断公告内容是否为附件
     * @param content
     * @return
     */
    public boolean isHasFile (String content) {
        String regex = "(href=\"|src=\"|href =\"|src =\"|href = \"|src = \").*?(zip|rar|7z|docx|doc|jpg|jpeg|png|ppt|xls|wps|xlsx)";
        Pattern pa = Pattern.compile(regex);
        Matcher ma = pa.matcher(content);
        if (ma.find()) {
            String newContent = chineseCompressUtil.getPlainText(content);
            newContent = MyStringUtils.deleteHtmlTag(newContent);
            newContent = newContent.replaceAll(" ", "");
            return newContent.length() < 100;
        }
        return false;
    }

    /**
     * 大写数字、阿拉伯数字、罗马数字统一转换为阿拉伯数字
     * 若是英文，转换至大写英文
     * @param str
     * @return
     */
    public String getNumStr (String str) {
        String regex = "[\\d一二三四五六七八九十ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]";
        Pattern pa = Pattern.compile(regex);
        Matcher ma = pa.matcher(str);
        StringBuilder numStr = new StringBuilder();
        while (ma.find()) {
            numStr.append(ma.group());
        }
        if (MyStringUtils.isNull(numStr.toString())) {
            String regex2 = "[A-Za-z]";
            pa = Pattern.compile(regex2);
            ma = pa.matcher(str);
            while (ma.find()) {
                numStr.append(ma.group());
            }
            return MyStringUtils.isNull(numStr.toString())?"":numStr.toString().toUpperCase();
        }
        return CNNumberFormat.numberFormat(numStr.toString());
    }

    /**
     * 返回关键字下标
     * 若存在多个关键字，返回-2
     * 无关键字返回-1
     * @param title
     * @return
     */
    public int keyWords3IndexOf (String title) {
        int a = -1;
        int b = -1;
        for (int i = 0; i < keyWords3.length; i++) {
            if (title.contains(keyWords3[i])) {
                a = i;
                break;
            }
        }
        if (a != -1) {
            for (int i = keyWords3.length-1; i < keyWords3.length; i--) {
                if (title.contains(keyWords3[i])) {
                    b = i;
                    break;
                }
            }
        }
        return a == b? a : -2;
    }

    /**
     * 公告过滤
     * @param searchResult
     * @param notice
     * @return
     */
    public List<Map<String,Object>> noticeFilter (List<Map<String,Object>> searchResult,EsNotice notice) {
        // 数据过滤
        logger.info("####  数据过滤 .. resultSize：" + searchResult.size() + "  ####");
        String title = notice.getTitle();

        // 标段过滤
        if (searchResult.size() > 1) {
            Iterator<Map<String,Object>> it = searchResult.iterator();
            while (it.hasNext()){
                String resultTitle = String.valueOf(it.next().get("title"));
                if (title.contains("标段")) {
                    if (!resultTitle.contains("标段")) {
                        it.remove();
                    }else {
                        // 取出两个标题中的数字或英文
                        String titleNumStr = null;
                        if (title.lastIndexOf("标段") != -1) {
                            titleNumStr = getNumStr(title.substring(0, title.lastIndexOf("标段")));
                        } else {
                            titleNumStr = getNumStr(title);
                        }
                        String resultTitleNumStr = null;
                        if (resultTitle.lastIndexOf("标段") != -1) {
                            resultTitleNumStr = getNumStr(resultTitle.substring(0, resultTitle.lastIndexOf("标段")));
                        } else {
                            resultTitleNumStr = getNumStr(resultTitle);
                        }
                        if (!titleNumStr.equals(resultTitleNumStr)) {
                            // 俩个标题的数字或英文不一致
                            it.remove();
                        }
                    }
                } else if (resultTitle.contains("标段")) {
                    // 新进公告没有标段，相关公告有标段
                    it.remove();
                }
                logger.info("####  标段过滤 ..  resultSize：" + searchResult.size() + "  ####");
            }
        }

        // 公告类型过滤
        if (searchResult.size() > 1) {
            Iterator<Map<String,Object>> it = searchResult.iterator();
            while (it.hasNext()) {
                String resultTitle = String.valueOf(it.next().get("title"));
                if (keyWords3IndexOf(resultTitle) != keyWords3IndexOf(title)) {
                    it.remove();
                }
            }
            logger.info("####  公告类型过滤 ..  resultSize：" + searchResult.size() + "  ####");
        }

        // 项目次数过滤
        if (searchResult.size() > 1) {
            Iterator<Map<String,Object>> it = searchResult.iterator();
            String regex = "(第).{1}?(次|批|包)";
            Pattern pa = Pattern.compile(regex);
            while (it.hasNext()) {
                String resultTitle = String.valueOf(it.next().get("title"));
                Matcher ma = pa.matcher(title);
                if (ma.find()) {
                    String sabi = ma.group();
                    ma = pa.matcher(resultTitle);
                    if (ma.find()) {
                        // 新公告与历史公告都存在第几次字段
                        int titleRegIndex = title.indexOf(sabi);
                        int titleKeyIndex = keyWordsIndex(title, keyWords4);
                        int historyTitleRegIndex = resultTitle.indexOf(sabi);
                        int histotyTitleKeyIndex = keyWordsIndex(resultTitle, keyWords4);
                        if (titleKeyIndex == -1 || titleKeyIndex > titleRegIndex) {
                            // 新进公告没有关键字或关键字在相关字段前
                            if (histotyTitleKeyIndex == -1 || histotyTitleKeyIndex > historyTitleRegIndex) {
                            } else {it.remove();}
                        } else {
                            if (histotyTitleKeyIndex == -1 || histotyTitleKeyIndex > historyTitleRegIndex) {
                                it.remove();
                            }
                        }
                    } else {
                        // 新公告存在第几次字段，历史公告无第几次字段,过滤掉
                        it.remove();
                    }
                } else {
                    // 新进公告无第几次字段，历史公告存在第几次字段，过滤掉
                    ma = pa.matcher(resultTitle);
                    if (ma.find()) {
                        it.remove();
                    }
                }
            }
            logger.info("####  项目次数过滤 ..  historyNotices：" + searchResult.size() + "  ####");
        }

        // 括号内容过滤
        if (searchResult.size() > 1) {
            if (contaninsBracket(title)) {
                int keyIndex = keyWordsIndex(title,keyWords5); // 获取第一个关键字的位置
                if (keyIndex != -1) {
                    String tempTitle = title.substring(0,keyIndex);
                    if (contaninsBracket(tempTitle)) {
                        Iterator<Map<String,Object>> it = searchResult.iterator();
                        while (it.hasNext()) {
                            String resultTitle = String.valueOf(it.next().get("title"));
                            if (!compareBracketStr(tempTitle,resultTitle)) {
                                it.remove();
                            }
                        }
                    }
                }
            }
            logger.info("####  括号内容过滤 .. resultSize: " + searchResult.size() + "  ####");
        }
        return searchResult;
    }


    /**
     * 公告过滤
     * @param notice 新进公告
     * @param historyNotices 历史公告
     */
    public List<EsNotice> noticeFilter(EsNotice notice, List<EsNotice> historyNotices) {
        // 历史公告过滤
        logger.info("@@@@  公告进行分类过滤!  @@@@");
        String title = notice.getTitle();

        // 标段过滤
        if (!historyNotices.isEmpty()) {
            Iterator<EsNotice> it = historyNotices.iterator();
            while (it.hasNext()) {
                String historyTitle = it.next().getTitle();
                if (title.contains("标段")) {
                    if (!historyTitle.contains("标段")) {
                        it.remove();
                    }
                    int titleBiaoduanIndex = title.lastIndexOf("标段");
                    int historyBiaoduanIndex = historyTitle.lastIndexOf("标段");
                    String titleNumStr = getNumStr(title.substring(0,titleBiaoduanIndex));
                    String historyTitleNumStr = getNumStr(historyTitle.substring(0,historyBiaoduanIndex));
                    if (!titleNumStr.equals(historyTitleNumStr)) {
                        it.remove();
                    }
                } else if (historyTitle.contains("标段")) {
                    it.remove();
                }
            }
            logger.info("####  标段过滤 ..  historyNotices：" + historyNotices.size() + "  ####");
        }

        // 括号内容过滤
        if (!historyNotices.isEmpty()) {
            if (contaninsBracket(title)) {
                int keyIndex = keyWordsIndex(title,keyWords5); // 获取第一个关键字的位置
                if (keyIndex != -1) {
                    String tempTitle = title.substring(0,keyIndex);
                    if (contaninsBracket(tempTitle)) {
                        Iterator<EsNotice> it = historyNotices.iterator();
                        while (it.hasNext()) {
                            String historyTitle = it.next().getTitle();
                            if (!compareBracketStr(tempTitle,historyTitle)) {
                                it.remove();
                            }
                        }
                    }
                }
            }
            logger.info("####  括号内容过滤 .. historyNotices: " + historyNotices.size() + "  ####");
        }
        return historyNotices;
    }

    /**
     * 比较括号中的内容
     * @param var1
     * @param var2
     * @return
     */
    public boolean compareBracketStr (String var1,String var2) {
        var2 = var2.replaceAll("[\\s~·`!！@￥$%^……&*（()）\\-——\\-_=+【\\[\\]】｛{}｝\\|、\\\\；;：:‘'“”\"，,《<。.》>、/？? ]","");
        boolean b = true;
        int startIndex = -1;
        int endIndex = -1;
        char[] a = var1.toCharArray();
        for (int i = 0; i < a.length; i++) {
            if (a[i] == '(' || a[i] == '（') {
                startIndex = i;
            }
            if (a[i] == ')' || a[i] == '）') {
                endIndex = i;
            }
            if (startIndex != -1 && endIndex != -1) {
                String key = var1.substring(startIndex + 1 , endIndex);
                key = key.replaceAll("[\\s~·`!！@￥$%^……&*（()）\\-——\\-_=+【\\[\\]】｛{}｝\\|、\\\\；;：:‘'“”\"，,《<。.》>、/？? ]","");
                startIndex = -1;
                endIndex = -1;
                if (keyWords6IndexOf(key) == -1) {
                    continue;
                }
                b = var2.contains(key);
                if (!b) {
                    return b;
                }
            }
        }
        return b;
    }

    public int keyWords6IndexOf (String title) {
        for (int i = 0; i < keyWords6.length; i++) {
            if (title.contains(keyWords6[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 是否存在括号
     * @param str
     * @return
     */
    public boolean contaninsBracket (String str) {
        return (str.contains("(") && str.contains(")")) || (str.contains("（") && str.contains("）"));
    }

    /**
     * 两条公告去重
     * @param notice
     */
    public boolean handleRepeat (EsNotice notice, EsNotice historyNotice) {
        if (notice.getRank() == 0 && historyNotice.getRank() != 0) {
            // 插入新进公告(省网)，isshow = 1
            notice.setIsShow(1);
            snatchNoticeHuNanDao.insertNewUrl(notice);
            // 插入公告内容
            int id = snatchNoticeHuNanDao.queryForInt("select max(id) from "+ RouteUtils.routeTableName("mishu.snatchurl",notice)+" where url=?", new Object[]{notice.getUrl()});
            snatchNoticeHuNanDao.insertSnatchContent(id, notice);
            snatchNoticeHuNanDao.insertCompress(notice, Long.valueOf((id)));//插入整理后的文档,去掉内容中有重复标题
            snatchNoticeHuNanDao.updateSnatchurlStatus(id,notice.getSource());
            logger.info("@@@@  新公告(省网)被去重 .. [redisId:"+notice.getRedisId()+"]title：" + notice.getTitle() + "  历史公告 : " + historyNotice.getTitle() + "  @@@@");
            return false;
        }

        if (notice.getRank() != 0 && historyNotice.getRank() == 0) {
            // 插入新进公告，历史公告isshow = 1
            notice.setEdit(historyNotice.getEdit());
            handleNotRepeat(notice);
            snatchNoticeHuNanDao.updateSnatchurlisShow(historyNotice.getUuid(),1,notice.getSource());

            if(historyNotice.getSource().equals(Constant.HUNAN_SOURCE)) {
                // 删除es上的历史公告索引
                if (historyNotice.getType() == 2) {
                    // 删除中标公告索引
                    snatchNoticeHuNanDao.deleteIndexById(IdxZhongbiaoSnatch.class, historyNotice.getUuid());
                } else {
                    // 删除招标公告索引
                    snatchNoticeHuNanDao.deleteIndexById(IdxZhaobiaoSnatch.class, historyNotice.getUuid());
                }
            }

            // 历史公告关联信息删除、编辑信息更改
            delRelationInfoAndEditDetail(notice,historyNotice);

            logger.info("@@@@  新公告入库，历史公告(省网)被去重 .. title：" + notice.getTitle() + "  历史公告 : " + historyNotice.getTitle() + "  @@@@");
            return true;
        }

        // 新进公告与历史公告都不是省网,保留市级
        if (notice.getRank() == 1 && historyNotice.getRank() == 2) {
            // 新公告update历史公告
            redisClear.clearRepeatNotice(historyNotice.getUuid());

            notice.setUuid(historyNotice.getUuid());
            notice.setEdit(historyNotice.getEdit());

            snatchNoticeHuNanDao.updateSnatchurlNotice(notice, historyNotice.getUuid());
            snatchNoticeHuNanDao.updateSnatchContent(notice);
            snatchNoticeHuNanDao.updateSnatchPress(notice);

            snatchNoticeHuNanDao.insertNoticeRepetition(historyNotice);
            //仅湖南数据处理es
            if (notice.getSource().equals(Constant.HUNAN_SOURCE)){
                if (notice.getType() == 2) { //中标
                    try {
                        snatchNoticeHuNanDao.insertZhongbiaoEsNotice(notice);
                    } catch (Exception e) {
                        logger.error("@@@@ES中标去重更新失败" + e);
                    }
                } else {
                    try {
                        snatchNoticeHuNanDao.updateZhaobiaoEsNotice(notice);
                    } catch (Exception e) {
                        logger.error("@@@@ES招标去重更新失败" + e);
                    }
                }
                // 历史公告关联信息删除、编辑信息更改
                delRelationInfoAndEditDetail(notice,historyNotice);
            }
            logger.info("@@@@  新公告替换历史公告 .. title: " + notice.getTitle() + "  历史公告 : " + historyNotice.getTitle() + "  @@@@");
            return true;
        }

        if (notice.getRank() == 2 && historyNotice.getRank() == 1) {
            // 新公告进去重表
            notice.setUuid(historyNotice.getUuid());
            snatchNoticeHuNanDao.insertNoticeRepetition(notice);
            logger.info("@@@@  新公告被历史公告去重 .. title: " + notice.getTitle() + "  历史公告 : " + historyNotice.getTitle() + "  @@@@");
            return false;
        }

        handleNotRepeat(notice);
        return true;
    }


    /**
     * 去除指定字符（只去除第一个）
     * @param c
     * @param str
     * @return
     */
    public String clearStrOnlyOne (String c, String str) {
        StringBuilder sb = new StringBuilder(str);
        if (str.contains(c) && sb.indexOf(c) != -1) {
            sb.delete(sb.indexOf(c),sb.indexOf(c) + c.length());
        }
        return sb.toString();
    }
    /**
     * 历史公告关联信息删除、编辑信息更改
     * @param notice
     * @param historyNotice
     */
    public void delRelationInfoAndEditDetail (EsNotice notice, EsNotice historyNotice) {
        snatchNoticeHuNanDao.deleteRelationInfo(Integer.valueOf(historyNotice.getUuid()));
        redisClear.clearGonggaoRelation(historyNotice.getUuid());   //清理公告关联信息缓存
        int noticeId = snatchNoticeHuNanDao.queryForInt("SELECT id FROM "+RouteUtils.routeTableName("mishu.snatchurl",notice)+" WHERE url = ? ",new Object[]{notice.getUrl()});
        // 编辑信息修改
        if (historyNotice.getEdit() == 1) { // 已编辑
            if (historyNotice.getType() == 2) { // 中标
                snatchNoticeHuNanDao.editZhongbiaoDetail(noticeId,Integer.valueOf(historyNotice.getUuid()));
            } else {
                snatchNoticeHuNanDao.editZhaobiaoDetail(noticeId,Integer.valueOf(historyNotice.getUuid()));
            }
        }
        // 资质信息修改
        snatchNoticeHuNanDao.updateSnatchUrlCert(noticeId,Integer.valueOf(historyNotice.getUuid()));
    }

}