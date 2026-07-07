package com.tripfit.tripfit.trip.domain;

import com.tripfit.tripfit.common.domain.BaseTimeEntity;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "참여자별 날짜·시간대 일정 응답")
public class MemberSchedule extends BaseTimeEntity {

	@Schema(description = "일정 응답 레코드 ID", example = "1")
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Schema(description = "응답한 여행방 참여자")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "trip_member_id", nullable = false)
	private TripMember tripMember;

	@Schema(description = "해당 날짜", example = "2026-08-03")
	@Column(nullable = false)
	private LocalDate scheduleDate;

	@Schema(description = "시간대 (오전/오후/저녁)")
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TimeSlot timeSlot;

	@Schema(description = "가용성 (가능/불가/미정)")
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ScheduleStatus status;

	// [제안] API에서 타인 노출 금지 (BR-TRIP-004)
	@Schema(description = "개인 메모. API에서 본인만 조회 (BR-TRIP-004)", nullable = true, example = "반차 사용 예정")
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
