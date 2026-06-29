package com.tripfit.tripfit.domain;

import com.tripfit.tripfit.domain.enums.TripStatus;
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
		name = "trip",
		uniqueConstraints = @UniqueConstraint(columnNames = "invite_code")
)
public class Trip extends SoftDeleteEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private LocalDate startRange;

	@Column(nullable = false)
	private LocalDate endRange;

	@Column(nullable = false)
	private Integer durationDays;

	@Column(nullable = false)
	private Integer targetMemberCount;

	@Column(nullable = false)
	private String inviteCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TripStatus status;

	@Column
	private LocalDate confirmedStartDate;

	@Column
	private LocalDate confirmedEndDate;

	protected Trip() {
	}

	public Trip(
			User owner,
			String name,
			LocalDate startRange,
			LocalDate endRange,
			Integer durationDays,
			Integer targetMemberCount,
			String inviteCode,
			TripStatus status
	) {
		this.owner = owner;
		this.name = name;
		this.startRange = startRange;
		this.endRange = endRange;
		this.durationDays = durationDays;
		this.targetMemberCount = targetMemberCount;
		this.inviteCode = inviteCode;
		this.status = status;
	}

	public Long getId() {
		return id;
	}

	public User getOwner() {
		return owner;
	}

	public void setOwner(User owner) {
		this.owner = owner;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public LocalDate getStartRange() {
		return startRange;
	}

	public void setStartRange(LocalDate startRange) {
		this.startRange = startRange;
	}

	public LocalDate getEndRange() {
		return endRange;
	}

	public void setEndRange(LocalDate endRange) {
		this.endRange = endRange;
	}

	public Integer getDurationDays() {
		return durationDays;
	}

	public void setDurationDays(Integer durationDays) {
		this.durationDays = durationDays;
	}

	public Integer getTargetMemberCount() {
		return targetMemberCount;
	}

	public void setTargetMemberCount(Integer targetMemberCount) {
		this.targetMemberCount = targetMemberCount;
	}

	public String getInviteCode() {
		return inviteCode;
	}

	public void setInviteCode(String inviteCode) {
		this.inviteCode = inviteCode;
	}

	public TripStatus getStatus() {
		return status;
	}

	public void setStatus(TripStatus status) {
		this.status = status;
	}

	public LocalDate getConfirmedStartDate() {
		return confirmedStartDate;
	}

	public void setConfirmedStartDate(LocalDate confirmedStartDate) {
		this.confirmedStartDate = confirmedStartDate;
	}

	public LocalDate getConfirmedEndDate() {
		return confirmedEndDate;
	}

	public void setConfirmedEndDate(LocalDate confirmedEndDate) {
		this.confirmedEndDate = confirmedEndDate;
	}
}
