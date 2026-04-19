package com.example.smsbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String name;

    @Column(length = 255)
    private String details;

    @Column(length = 255)
    private String address;

    @Column(length = 120)
    private String city;

    @Column(length = 120)
    private String state;

    @Column(length = 32)
    private String postalCode;

    @Column(length = 120)
    private String country;

    @Column(length = 32)
    private String phone;

    @Column(nullable = false)
    private boolean isAlarmReceiverIncluded;

    @Column(nullable = false)
    private boolean alarmReceiverEnabled;

    @Lob
    @Column(name = "alarm_receiver_config")
    private String alarmReceiverConfigJson;

    @Lob
    @Column(name = "whitelisted_dns")
    private String whitelistedDnsJson;

    @Lob
    @Column(name = "whitelisted_ips")
    private String whitelistedIpsJson;

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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isAlarmReceiverIncluded() {
        return isAlarmReceiverIncluded;
    }

    public void setAlarmReceiverIncluded(boolean alarmReceiverIncluded) {
        isAlarmReceiverIncluded = alarmReceiverIncluded;
    }

    public boolean isAlarmReceiverEnabled() {
        return alarmReceiverEnabled;
    }

    public void setAlarmReceiverEnabled(boolean alarmReceiverEnabled) {
        this.alarmReceiverEnabled = alarmReceiverEnabled;
    }

    public String getAlarmReceiverConfigJson() {
        return alarmReceiverConfigJson;
    }

    public void setAlarmReceiverConfigJson(String alarmReceiverConfigJson) {
        this.alarmReceiverConfigJson = alarmReceiverConfigJson;
    }

    public String getWhitelistedDnsJson() {
        return whitelistedDnsJson;
    }

    public void setWhitelistedDnsJson(String whitelistedDnsJson) {
        this.whitelistedDnsJson = whitelistedDnsJson;
    }

    public String getWhitelistedIpsJson() {
        return whitelistedIpsJson;
    }

    public void setWhitelistedIpsJson(String whitelistedIpsJson) {
        this.whitelistedIpsJson = whitelistedIpsJson;
    }
}
