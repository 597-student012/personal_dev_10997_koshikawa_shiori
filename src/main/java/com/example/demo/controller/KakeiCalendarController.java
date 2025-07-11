package com.example.demo.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.demo.entity.Kakei;
import com.example.demo.entity.KakeiboWithCategory;
import com.example.demo.model.AccountModel;
import com.example.demo.model.KakeiModel;
import com.example.demo.repository.KakeiRepository;
import com.example.demo.repository.KakeiboWithCategoryRepository;
import com.example.demo.service.KakeiService;

@Controller

public class KakeiCalendarController {

	@Autowired
	HttpSession session;

	@Autowired
	KakeiModel kakeiModel;

	//	@Autowired
	//	Kakei kakei;

	@Autowired
	KakeiboWithCategoryRepository kakeiboWithCategoryRepository;

	@Autowired
	AccountModel accountModel;

	private final KakeiService kakeiService;

	@Autowired
	KakeiRepository kakeiRepository;

	public KakeiCalendarController(KakeiService kakeiService) {
		this.kakeiService = kakeiService;
	}

	@GetMapping("/calendar")
	public String index(@RequestParam(value = "year", required = false) Integer year,
			@RequestParam(value = "month", required = false) Integer month, Model model) {
		LocalDate today = LocalDate.now();

		//nullの場合は今日の年月を取得
		int currentYear = (year != null) ? year : today.getYear();
		int currentMonth = (month != null) ? month : today.getMonthValue();

		LocalDate firstDay = LocalDate.of(currentYear, currentMonth, 1);
		LocalDate prevMonth = firstDay.minusMonths(1);
		LocalDate nextMonth = firstDay.plusMonths(1);

		//userIdと月ごとのデータの取得
		Integer userId = accountModel.getId();
		java.sql.Date sqlFirstDay = java.sql.Date.valueOf(firstDay);
		java.sql.Date sqlEndDay = java.sql.Date.valueOf(nextMonth.minusDays(1));

		List<KakeiboWithCategory> expenses = kakeiboWithCategoryRepository.findByUserIdAndDateBetween(userId,
				sqlFirstDay, sqlEndDay);
		int firstDayOfWeek = firstDay.getDayOfWeek().getValue();
		firstDayOfWeek = (firstDayOfWeek == 7) ? 0 : firstDayOfWeek;
		int daysInMonth = firstDay.lengthOfMonth();

		//priceの合計金額計算をする
		//streamで処理をまとめる、mapToIntでIntegerをIntに、
		List<Integer> priceList = kakeiRepository.findPricesByUserIdAndDateBetween(
				userId, firstDay, nextMonth.minusDays(1));
		int totalPrice = priceList.stream()
				.filter(Objects::nonNull)
				.mapToInt(Integer::intValue)
				.sum();
		model.addAttribute("totalPrice", totalPrice);

		model.addAttribute("year", currentYear);
		model.addAttribute("month", currentMonth);
		model.addAttribute("expenses", expenses);
		model.addAttribute("firstDayOfWeek", firstDayOfWeek);
		model.addAttribute("daysInMonth", daysInMonth);
		model.addAttribute("prevYear", prevMonth.getYear());
		model.addAttribute("prevMonth", prevMonth.getMonthValue());
		model.addAttribute("nextYear", nextMonth.getYear());
		model.addAttribute("nextMonth", nextMonth.getMonthValue());

		return "calendar";

	}

	@GetMapping("/dailyList")
	public String showDailyList(@RequestParam("year") int year, @RequestParam("month") int month,
			@RequestParam("day") int day, Model model) {
		kakeiModel.setYear(year);
		kakeiModel.setDay(day);
		kakeiModel.setMonth(month);
		model.addAttribute("day", day);
		model.addAttribute("month", month);
		model.addAttribute("year", year);

		LocalDate date = LocalDate.of(year, month, day);
		Integer userId = accountModel.getId();
		System.out.println(userId);
		model.addAttribute("kakeiboData",
				kakeiboWithCategoryRepository.findByUserIdAndDate(userId, date));
		return "dailyList";
	}

	@GetMapping("/record/add")
	public String newRecord() {
		return "recordAdd";
	}

	//新規追加画面
	@PostMapping("/record/add")
	public String add(
			@RequestParam(name = "date", defaultValue = "") LocalDate date,
			//入力フォームから情報取得（viewなので、categoryIdを使う）
			@RequestParam(name = "categoryId", defaultValue = "") Integer categoryId,
			@RequestParam(name = "title", defaultValue = "") String title,
			@RequestParam(name = "detail", defaultValue = "") String detail,
			@RequestParam(name = "price", defaultValue = "") Integer price,
			Model model) {

		if (date == null || categoryId == null || title.equals("") || detail.equals("") || price == null) {
			model.addAttribute("message", "空欄があります");
			return "recordAdd";
		}

		model.addAttribute("date", date);
		model.addAttribute("categoryId", categoryId);
		model.addAttribute("title", title);
		model.addAttribute("detail", detail);
		model.addAttribute("price", price);

		//モデルのuserIdを取得
		Integer userId = accountModel.getId();
		Kakei kakei = new Kakei();
		kakei.setDate(date);
		kakei.setCategoryId(categoryId);
		kakei.setTitle(title);
		kakei.setDetail(detail);
		kakei.setPrice(price);
		kakei.setUserId(userId);
		kakeiRepository.save(kakei);

		LocalDate localDate = date;
		return "redirect:/dailyList?year=" + localDate.getYear() +
				"&month=" + localDate.getMonthValue() +
				"&day=" + localDate.getDayOfMonth();

	}

	//更新画面表示
	@GetMapping("/record/edit/{id}")
	public String edit(@PathVariable("id") Integer id, Model model) {

		Kakei kakei = kakeiRepository.findById(id).get();
		model.addAttribute("kakei", kakei);
		return "recordEdit";
	}

	//更新処理
	@PostMapping("/record/edit/{id}")
	public String update(@PathVariable("id") Integer id,
			@RequestParam(name = "date", defaultValue = "") LocalDate date,
			//入力フォームから情報取得（viewなので、categoryIdを使う）
			@RequestParam(name = "categoryId", defaultValue = "") Integer categoryId,
			@RequestParam(name = "title", defaultValue = "") String title,
			@RequestParam(name = "detail", defaultValue = "") String detail,
			@RequestParam(name = "price", defaultValue = "") Integer price, Model model) {

		Integer userId = accountModel.getId();
		Kakei kakei = kakeiRepository.findById(id).get();

		boolean hasError = false;

		// 空欄の場合は元の値を使う
		if (date == null) {
			date = kakei.getDate();
			hasError = true;
		}
		if (categoryId == null) {
			categoryId = kakei.getCategoryId();
			hasError = true;
		}
		if (title == null || title.isEmpty()) {
			title = kakei.getTitle();
			hasError = true;
		}
		if (detail == null || detail.isEmpty()) {
			detail = kakei.getDetail();
			hasError = true;
		}
		if (price == null) {
			price = kakei.getPrice();
			hasError = true;
		}

		// 最新の値を再セット
		kakei.setDate(date);
		kakei.setCategoryId(categoryId);
		kakei.setTitle(title);
		kakei.setDetail(detail);
		kakei.setPrice(price);
		kakei.setUserId(userId);

		if (hasError) {
			model.addAttribute("message", "空欄があったため、元の値で補完しました");
			model.addAttribute("kakei", kakei);
			return "recordEdit";
		}

		//	空欄になってしまうエラー処理
		//		kakei.setDate(date);
		//		kakei.setCategoryId(categoryId);
		//		kakei.setTitle(title);
		//		kakei.setDetail(detail);
		//		kakei.setPrice(price);
		//		kakei.setUserId(userId);
		//
		//		if (date == null || categoryId == null || title.equals("") || detail.equals("") || price == null) {
		//			model.addAttribute("message", "空欄があります");
		//			model.addAttribute("kakei", kakei);
		//			return "recordEdit";
		//		}

		kakeiRepository.save(kakei);
		LocalDate localDate = date;
		return "redirect:/dailyList?year=" + localDate.getYear() +
				"&month=" + localDate.getMonthValue() +
				"&day=" + localDate.getDayOfMonth();

		//新規追加も、更新も、セッションが切れたらリセットされる
	}

	//削除画面の表示
	@GetMapping("/record/delete/{id}")
	public String confirmDelete(@PathVariable("id") Integer id, Model model) {

		Kakei kakei = kakeiRepository.findById(id).get();
		model.addAttribute("kakei", kakei);
		return "recordDelete";
	}

	//削除処理
	@PostMapping("/record/delete/{id}")
	public String delete(@PathVariable("id") Integer id, @RequestParam(name = "date") LocalDate date) {
		//		Kakei kakei = kakeiRepository.findById(id).get();

		kakeiRepository.deleteById(id);

		LocalDate localDate = date;
		return "redirect:/dailyList?year=" + localDate.getYear() +
				"&month=" + localDate.getMonthValue() +
				"&day=" + localDate.getDayOfMonth();

	}

}
