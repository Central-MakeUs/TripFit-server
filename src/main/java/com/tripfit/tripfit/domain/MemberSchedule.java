package com.tripfit.tripfit.domain;

import com.tripfit.tripfit.domain.enums.ScheduleStatus;
import com.tripfit.tripfit.domain.enums.TimeSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

@Entity
@Table(
		name = "member_schedule",
		uniqueConstraints = @UniqueConstraint(columnNames = {"trip_member_id", "schedule_date", "time_slot"})
)
public class MemberSchedule extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "trip_member_id", nullable = false)
	private TripMember tripMember;

	@Column(nullable = false)
	private LocalDate scheduleDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TimeSlot timeSlot;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ScheduleStatus status;

	// [제안] API에서 타인 노출 금지 (BR-TRIP-004)
	@Column
	private String note;

	protected MemberSchedule() {
	}

	public MemberSchedule(
			TripMember tripMember,
			LocalDate scheduleDate,
			TimeSlot timeSlot,
			ScheduleStatus status,
			String note
	) {
		this.tripMember = tripMember;
		this.scheduleDate = scheduleDate;
		this.timeSlot = timeSlot;
		this.status = status;
		this.note = note;
	}

	public Long getId() {
		return id;
	}

	public TripMember getTripMember() {
		return tripMember;
	}

	public void setTripMember(TripMember tripMember) {
		this.tripMember = tripMember;
	}

	public LocalDate getScheduleDate() {
		return scheduleDate;
	}

	public void setScheduleDate(LocalDate scheduleDate) {
		this.scheduleDate = scheduleDate;
	}

	public TimeSlot getTimeSlot() {
		return timeSlot;
	}

	public void setTimeSlot(TimeSlot timeSlot) {
		this.timeSlot = timeSlot;
	}

	public ScheduleStatus getStatus() {
		return status;
	}

	public void setStatus(ScheduleStatus status) {
		this.status = status;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}
}
