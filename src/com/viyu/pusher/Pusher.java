package com.viyu.pusher;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.GridPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import com.viyu.mail.MailHandler;
import com.viyu.parser.BoardInfo;
import com.viyu.parser.NewsmthParser;

public class Pusher extends Application {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void start(Stage stage) {
		final WebView view = new WebView();
		GridPane grid = new GridPane();
		grid.setHgap(2.0d);
		grid.setVgap(2.0d);
		final CheckBox[] boardChecks = new CheckBox[BoardInfo.values().length];
		int i = 0;
		for(BoardInfo bi : BoardInfo.values()) {
			CheckBox check  = new CheckBox(bi.getBoardName());
			grid.add(check, i, 0);
			boardChecks[i++] = check;
		}
		final String date = DateFormat.getDateInstance(DateFormat.SHORT, Locale.CHINA).format(new Date());
		{
			Button button = new Button("Parse");
			button.setLayoutX(10);
			button.setLayoutY(50);
			button.setOnAction(new EventHandler() {
				@Override
				public void handle(Event arg0) {
					List<BoardInfo> selectedBoards = new ArrayList<>();
					for(CheckBox cb : boardChecks) {
						if(cb != null && cb.isSelected()) {
							selectedBoards.add(BoardInfo.getInstance(cb.getText()));
						}
					}
					
					List<String> parms = getParameters().getRaw();
					MailHandler mailHandler = new MailHandler(MailHandler.HOST_QQ,
							MailHandler.PORT_465, MailHandler.debug_false, parms.get(0),
							parms.get(1), parms.get(2), parms.get(3));
					new NewsmthParser(mailHandler, selectedBoards, view, date.split("-")[2]);
				}
			});
			grid.add(button, 0, 1);
		}
		{
			Button button = new Button("Exit");
			button.setLayoutX(200);
			button.setLayoutY(50);
			button.setOnAction(new EventHandler() {
				@Override
				public void handle(Event arg0) {
					Platform.exit();
				}
			});
			grid.add(button, 1, 1);
		}
		grid.add(view, 0, 2);
		Scene scene = new Scene(grid, 800, 200);
		stage.setScene(scene);
		stage.setTitle(date);
		stage.show();
	}

	public static void main(String[] args) {
		launch(args);
	}
}
