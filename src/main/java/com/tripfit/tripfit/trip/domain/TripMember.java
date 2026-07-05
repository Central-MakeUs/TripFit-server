package com.tripfit.tripfit.trip.domain;

import com.tripfit.tripfit.common.domain.BaseTimeEntity;
import com.tripfit.tripfit.user.domain.User;
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

import java.time.LocalDateTime;

@Entity
@Table(
		name = "trip_member",
		uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "user_id"})
)
public class TripMember extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "trip_id", nullable = false)
	private Trip trip;

	// TODO(BR-USER-002): 비회원 링크 참여 시 nullable vs 로그인 강제 정책 확정 필요
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TripMemberRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TripMemberStatus status;

	@Column(nullable = false)
	private LocalDateTime joinedAt;

	protected TripMember() {
	}

	public TripMember(Trip trip, User user, TripMemberRole role, TripMemberStatus status, LocalDateTime joinedAt) {
		this.trip = trip;
		this.user = user;
		this.role = role;
		this.status = status;
		this.joinedAt = joinedAt;
	}

	public Long getId() {
		return id;
	}

	public Trip getTrip() {
		return trip;
	}

	public void setTrip(Trip trip) {
		this.trip = trip;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public TripMemberRole getRole() {
		return role;
	}

	public void setRole(TripMemberRole role) {
		this.role = role;
	}

	public TripMemberStatus getStatus() {
		return status;
	}

	public void setStatus(TripMemberStatus status) {
		this.status = status;
	}

	public LocalDateTime getJoinedAt() {
		return joinedAt;
	}

	public void setJoinedAt(LocalDateTime joinedAt) {
		this.joinedAt = joinedAt;
	}
}
