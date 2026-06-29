package com.tripfit.tripfit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalTime;

@Entity
@Table(name = "user_condition")
public class UserCondition extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column
	private String workDays;

	@Column
	private LocalTime workStartTime;

	@Column
	private LocalTime workEndTime;

	@Column
	private Integer maxVacationDays;

	@Column
	private String vacationApplyPeriod;

	@Column(name = "is_half_vacation_available", nullable = false)
	private boolean halfVacationAvailable;

	@Column(name = "is_holiday_rest", nullable = false)
	private boolean holidayRest;

	protected UserCondition() {
	}

	public UserCondition(
			User user,
			String workDays,
			LocalTime workStartTime,
			LocalTime workEndTime,
			Integer maxVacationDays,
			String vacationApplyPeriod,
			boolean halfVacationAvailable,
			boolean holidayRest
	) {
		this.user = user;
		this.workDays = workDays;
		this.workStartTime = workStartTime;
		this.workEndTime = workEndTime;
		this.maxVacationDays = maxVacationDays;
		this.vacationApplyPeriod = vacationApplyPeriod;
		this.halfVacationAvailable = halfVacationAvailable;
		this.holidayRest = holidayRest;
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public String getWorkDays() {
		return workDays;
	}

	public void setWorkDays(String workDays) {
		this.workDays = workDays;
	}

	public LocalTime getWorkStartTime() {
		return workStartTime;
	}

	public void setWorkStartTime(LocalTime workStartTime) {
		this.workStartTime = workStartTime;
	}

	public LocalTime getWorkEndTime() {
		return workEndTime;
	}

	public void setWorkEndTime(LocalTime workEndTime) {
		this.workEndTime = workEndTime;
	}

	public Integer getMaxVacationDays() {
		return maxVacationDays;
	}

	public void setMaxVacationDays(Integer maxVacationDays) {
		this.maxVacationDays = maxVacationDays;
	}

	public String getVacationApplyPeriod() {
		return vacationApplyPeriod;
	}

	public void setVacationApplyPeriod(String vacationApplyPeriod) {
		this.vacationApplyPeriod = vacationApplyPeriod;
	}

	public boolean isHalfVacationAvailable() {
		return halfVacationAvailable;
	}

	public void setHalfVacationAvailable(boolean halfVacationAvailable) {
		this.halfVacationAvailable = halfVacationAvailable;
	}

	public boolean isHolidayRest() {
		return holidayRest;
	}

	public void setHolidayRest(boolean holidayRest) {
		this.holidayRest = holidayRest;
	}
}
