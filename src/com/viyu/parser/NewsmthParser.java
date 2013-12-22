package com.viyu.parser;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.w3c.dom.Document;

import com.viyu.mail.MailHandler;

public class NewsmthParser implements ChangeListener<State> {

	private WebEngine engine = null;
	private MailHandler mailHandler = null;
	
	private List<BoardInfo> boards = null;
	private int boardParsedIndex = 0;
	private Map<String, String> subjectMap = new HashMap<String, String>();
	Iterator<Entry<String, String>> subjectMapIter = null;
	private boolean isParsingBoard = true;
	private StringBuffer subjectCache = new StringBuffer();
	
	private static final String SEPARATOR_NEW_LINE = "\n";
	private static final String SEPARATOR_BLANK_SPACE = " ";
	private static final String SEPARATOR_SUBJECT = "_____________________________________________" + SEPARATOR_NEW_LINE;
	 
	private String today = null;
	
	private int currentPageOfBoard = -1;
	private boolean isFirstFlip = true;//在一个board，第一次点上一页，内容会重复
	
	private int subjectPushedCount = 0;
	
	private WebView view = null;
	
	public NewsmthParser(MailHandler handler, List<BoardInfo> boards, WebView view, String today) {
		this.mailHandler = handler;
		this.boards = boards;
		this.today = today;
		
		this.view = view;
		engine = view.getEngine();
		engine.getLoadWorker().stateProperty().addListener(NewsmthParser.this);
		//加载第一个board
		if(boardParsedIndex < boards.size()) {
			engine.load(boards.get(boardParsedIndex).getBoardURL());
		}
	}
	
	@Override
	public void changed(ObservableValue<? extends State> ov, State oldState, State newState) {
		if(newState == State.FAILED) {
			System.out.println("state failed");
		}
		if(newState == State.CANCELLED) {
			System.out.println("state cancelled");
		}
		if(newState == State.READY) {
			System.out.println("state ready");
		}
		if(newState == State.SCHEDULED) {
			System.out.println("state scheduled");
		}
		if (newState == State.SUCCEEDED) {
			System.out.println("state succeeded");
			try {
				Thread.sleep(1000);
			} catch(InterruptedException ex) {
				ex.printStackTrace();
			}
			Document doc = engine.getDocument();
			if(isParsingBoard) {
				//解析board
				parseBoard(doc);
				if(currentPageOfBoard < 2) { //没必要加载上一页，当前board加载结束
					currentPageOfBoard = -1;
					isFirstFlip = true;
					subjectMapIter = subjectMap.entrySet().iterator();
					//之后切换为加载该board下的subject
					isParsingBoard = false;
					//加载当前board中的第一篇subject
					if(subjectMapIter.hasNext()) {
						Entry<String, String> entry = subjectMapIter.next();
						engine.load(boards.get(boardParsedIndex).getArticleURL(entry.getKey()));
					}
				} else {
					if(isFirstFlip) { //第一次点上一页，由于内容会部分重复，所以先不减
						isFirstFlip = false;
					} else {
						currentPageOfBoard--;
					}
					engine.load(boards.get(boardParsedIndex).getBoardURL(currentPageOfBoard));
				}
				
			} else {
				//加载一个subject
				String content = parseSubject(doc);
				if(content != null) {
					subjectCache.append(SEPARATOR_SUBJECT);
					subjectCache.append(content);
					subjectPushedCount++;
				}
				//如果当前boards下的subject还没加载完，继续加载
				if(subjectMapIter.hasNext()) {
					Entry<String, String> entry = subjectMapIter.next();
					engine.load(boards.get(boardParsedIndex).getArticleURL(entry.getKey()));
				} else { //当前boards下的subject加载完了，推送之后切换到加载下一个board
					//
					mailHandler.pushContent(boards.get(boardParsedIndex).getBoardName() + "-" + subjectPushedCount + "_" + today, subjectCache.toString());
					System.out.println("There are " + subjectPushedCount + " articles have been pushed!");
					subjectCache.delete(0, subjectCache.length());
					subjectPushedCount = 0;
					//
					isParsingBoard = true;
					subjectMap.clear();
					boardParsedIndex++;
					if(boardParsedIndex < boards.size()) {
						engine.load(boards.get(boardParsedIndex).getBoardURL());
					}
				}
			}
		}
	}

	private static final Pattern pagePattern = Pattern.compile("page=\\d+");
	private static final String  CLASS_ODD = "odd";
	private static final String CLASS_EVEN = "enen";
	private static final String TAG_FORM = "form";
	private static final int INDEX_PREVIOUS_PAGE = 1;
	private static final int INDEX_END_PAGE = 5;
	/**
	 * boards->board
	 */
	private void parseBoard(Document doc) {
		try {
			Transformer transformer = TransformerFactory.newInstance()
					.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			transformer.transform(new DOMSource(doc), new StreamResult(
					new OutputStreamWriter(output, "UTF-8")));
			//
			Element body = Jsoup.parseBodyFragment(output.toString("UTF-8")).body();
			//
			boolean previousDayComing = parseGidAndDate(body.getElementsByClass(CLASS_ODD));
			if(previousDayComing == true) { //已经发现了前一天的帖子
				parseGidAndDate(body.getElementsByClass(CLASS_EVEN));//继续加载偶数帖子，不用再关心这次是否能发现前一天的帖子了
			} else {
				previousDayComing = parseGidAndDate(body.getElementsByClass(CLASS_EVEN));//继续试图发现前一天的帖子
			}
			if(previousDayComing == true) {//已经发现前一天的帖子，board加载结束，不用再上一页了
				currentPageOfBoard = -1;
			} else if(currentPageOfBoard > 1){ //在board的上一页加载中已经找到了page number，不必再去找了
			} else {
				//分析上一页的page，返回去让其再加载
				Elements haha = body.getElementsByTag(TAG_FORM);
				Matcher m = pagePattern.matcher(haha.html());
				int i = 0;		
				while(m.find()) {
					if(i == INDEX_PREVIOUS_PAGE) {
						String str = m.group();
						currentPageOfBoard = Integer.parseInt(str.substring(INDEX_END_PAGE, str.length()));
						break;
					}
					i++;
				}
			}
		} catch (TransformerException | UnsupportedEncodingException ex) {
			ex.printStackTrace();
		}
	}
	
	private static final Pattern gidPattern = Pattern.compile("gid=\\d+");
	private static final String TAG_NOBR = "nobr";
	private static final int COUNT_TAG_NOBR = 4;
	/**
	 * boards->board->gid
	 * @return 发现至少有一个gid不是当前date发的，则返回false，通知parseBoard停止，不用翻页了
	 */
	private boolean parseGidAndDate(Elements eles) {
		boolean previousDay = false;
		for(Element ele : eles) {
			String date = ele.getElementsByTag(TAG_NOBR).text();
			date = date.substring(COUNT_TAG_NOBR, date.length()).trim();
			Matcher mGid = gidPattern.matcher(ele.html());
			String gid = null;
			if(mGid.find())
				gid = mGid.group();
			if(date != null && gid != null) {
				if(date.equals(today)) { 
					subjectMap.put(gid.substring(4,  gid.length()), date);
				} else {
					previousDay = true;
				}
			}
		}
		return previousDay;
	}
	
	private static final String TAG_TITLE = "title";
	private static final String TAG_ARTICLE = "article";
	
	/**
	 * boards->board->subject
	 */
	private String parseSubject(Document doc) {
		try {
			Transformer transformer = TransformerFactory.newInstance()
					.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			transformer.transform(new DOMSource(doc), new StreamResult(
					new OutputStreamWriter(output, "UTF-8")));
			//
			String content = output.toString("UTF-8");
			Element body = Jsoup.parseBodyFragment(content).body();
			Elements title = body.getElementsByTag(TAG_TITLE);
			if(!isNotMeaning(title.text())) {
				Elements eles = body.getElementsByClass(TAG_ARTICLE);
				StringBuffer strBuff = new StringBuffer();
				int i = 0;
				String host = null;
				for(Element ele : eles) {
					String[] strs = parseArticle(ele.html(), host);
					if(i == 0 && strs[1] != null) {
						host = strs[1];
					}
					if(strs[0] != null) {
						i++;
						strBuff.append(strs[0] + SEPARATOR_NEW_LINE);
					}
				}
				return strBuff.toString();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}
	
	private static final String MSG_CANCELED = "被取消";
	private static final String MSG_FAWENQX = "发文权限";
	
	/**
	 * 根据boards->board->subject的标题过滤不推送的帖子
	 */
	private boolean isNotMeaning(String title) {
		return title.contains(MSG_CANCELED) & title.contains(MSG_FAWENQX);
	}
	
	private static final String TAG_BR = "<br";
	private static final String TAG_SPAN = "<span";
	private static final String TAG_MIDDLE_OF_TITLE = "&nbsp;";
	private static final String TAG_DY = "&gt;";
	private static final String TAG_START_QMD = "<br />--";
	private static final String TAG_HREF = "href";
	private static final String TAG_NEWSMTH_NET = "newsmth.net";
	private static final String TAG_FROM = "[FROM";
	private static final String TAG_STARS = "※";
	private static final String TAG_MAO_WEN = ":?";
	private static final String TAG_FAXINZHAN = "发信站:";
	private static final String TAG_IMG = "<img";
	private static final String TAG_DAZUOZHONG = "的大作中提到:";
	private static final String TAG_DIV = "<div";

	private static final int TAG_COUNT_BR = 6;//<br />
	
	/**
	 * boards->board->subject->article
	 */
	private String[] parseArticle(String html, String host) {
		String[] strs = html.split(SEPARATOR_NEW_LINE);
		StringBuffer strBuff = new StringBuffer();
		String sender = null;
		for(String str : strs) {
			if(str.equals(SEPARATOR_BLANK_SPACE)) {
			} else if(str.contains(TAG_HREF) || 
					str.contains(TAG_NEWSMTH_NET) || 
					str.contains(TAG_FROM) ||
					str.contains(TAG_STARS) ||
					str.contains(TAG_MAO_WEN) ||
					str.contains(TAG_FAXINZHAN) ||
					str.contains(TAG_IMG) ||
					str.contains(TAG_DIV) ||
					(str.contains("发自") && str.contains("版")) ||
					(host != null && str.contains(TAG_MIDDLE_OF_TITLE)) ||
					(host != null && str.contains(TAG_DAZUOZHONG))) {
				
			} else if(str.startsWith(TAG_SPAN)){
			} else if(str.startsWith(TAG_START_QMD)) {
				break; //去掉签名档
			} else if(str.startsWith(TAG_BR)){
				String cache = ingoreStrings(str.substring(TAG_COUNT_BR, str.length()));
				if(!cache.equals(" ") || cache.length() > 1) {
					if(cache.startsWith("标 题:")) {
						strBuff.append(cache.substring(3, cache.length()) + SEPARATOR_NEW_LINE);
					} else {
						strBuff.append(cache + SEPARATOR_NEW_LINE);
					}
				}
			} else {
				String cache = ingoreStrings(str);
				if(!cache.equals(" ") || cache.length() > 1) {
					if(cache.contains("发信人") && cache.contains("信区")) {
						Matcher m = idPattern.matcher(cache);
						if(m.find()) {
							cache = m.group();
							sender = cache;
							if(host != null) {
								if(sender.equals(host)) {
									strBuff.append(cache + ": :");
								}  else {
									strBuff.append(cache + ": ");
								}
							} else {
								strBuff.append(cache + ": ");
							}
						}
					} else {
						strBuff.append(cache + "\n");
					}
				}
			}
		}
		return new String[]{strBuff.toString(), sender};
	}
	
	private static final Pattern idPattern = Pattern.compile("[\\w]+");
	/**
	 * 忽略不需要的字符
	 */
	private String ingoreStrings(String message) {
		if(message.contains(TAG_MIDDLE_OF_TITLE)) {
			message = message.replace(TAG_MIDDLE_OF_TITLE, "");
		}
		if(message.contains(TAG_DY)) {
			message = message.replace(TAG_DY, ">");
		}
		return message;
	}
}
