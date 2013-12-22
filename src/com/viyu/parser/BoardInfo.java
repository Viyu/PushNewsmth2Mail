package com.viyu.parser;


public enum BoardInfo {

	FamilyLife("familylife"), Java("java"), WorkLife("worklife"), AutoWorld("autoworld"),
	BeijingSouthwest("BeijingSouthwest"), DSLR("dslr"), MobileDev("MobileDev"), Movie("Movie");

	private static final String URL_PREFIX_ARTICLE = "http://www.newsmth.net/bbstcon.php?board=";
	private static final String URL_SUFFIX_ARTICLE = "&gid=";
	private static final String URL_PREFIX_BOARD = "http://www.newsmth.net/bbsdoc.php?board=";
	private static final String URL_SUFFIX_BOARD = "&ftype=6";
	private static final String URL_PREFIX_PAGE = "&page=";
	private String boardName = null;

	private BoardInfo(String name) {
		this.boardName = name;
	}
	
	public static BoardInfo getInstance(String boardName) {
		switch(boardName.toLowerCase()) {
		case "autoworld":
			return AutoWorld;
		case "beijingsouthwest":
			return BeijingSouthwest;
		case "dslr":
			return DSLR;
		case "mobiledev":
			return MobileDev;
		case "movie":
			return Movie;
		case "familylife":
			return FamilyLife;
		case "java":
			return Java;
		case "worklife":
			return WorkLife;
		}
		return null;
	}

	public String getBoardName() {
		return this.boardName;
	}

	public String getBoardURL() {
		return URL_PREFIX_BOARD + this.boardName + URL_SUFFIX_BOARD;
	}
	
	public String getBoardURL(int page) {
		return URL_PREFIX_BOARD + this.boardName + URL_SUFFIX_BOARD + URL_PREFIX_PAGE + page;
	}

	public String getArticleURL(String gid) {
		return URL_PREFIX_ARTICLE + this.boardName + URL_SUFFIX_ARTICLE + gid;
	}
}