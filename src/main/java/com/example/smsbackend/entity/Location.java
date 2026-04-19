package com.example.smsbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String details;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "alarm_receiver_account_number", length = 80)
    private String alarmReceiverAccountNumber;

    @Column(name = "alarm_receiver_enabled", nullable = false)
    private boolean alarmReceiverEnabled;

    @Column(name = "alarm_receiver_users", length = 2000)
    private String alarmReceiverUsersJson;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getAlarmReceiverAccountNumber() {
        return alarmReceiverAccountNumber;
    }

    public void setAlarmReceiverAccountNumber(String alarmReceiverAccountNumber) {
        this.alarmReceiverAccountNumber = alarmReceiverAccountNumber;
    }

    public boolean isAlarmReceiverEnabled() {
        return alarmReceiverEnabled;
    }

    public void setAlarmReceiverEnabled(boolean alarmReceiverEnabled) {
        this.alarmReceiverEnabled = alarmReceiverEnabled;
    }

    public String getAlarmReceiverUsersJson() {
        return alarmReceiverUsersJson;
    }

    public void setAlarmReceiverUsersJson(String alarmReceiverUsersJson) {
        this.alarmReceiverUsersJson = alarmReceiverUsersJson;
    }
}
