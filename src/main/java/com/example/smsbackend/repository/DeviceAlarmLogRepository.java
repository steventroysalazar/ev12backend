package com.example.smsbackend.repository;

import com.example.smsbackend.entity.DeviceAlarmLog;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceAlarmLogRepository extends JpaRepository<DeviceAlarmLog, Long> {
    List<DeviceAlarmLog> findByDeviceIdOrderByEventAtDesc(Long deviceId);

    @Query("""
        select distinct l.alarmCode
        from DeviceAlarmLog l
        where l.alarmCode is not null and trim(l.alarmCode) <> ''
        order by l.alarmCode asc
        """)
    List<String> findDistinctAlarmCodes();

    @Query("""
        select distinct l.action
        from DeviceAlarmLog l
        where l.action is not null and trim(l.action) <> ''
        order by l.action asc
        """)
    List<String> findDistinctActions();

    @Query("""
        select distinct l.source
        from DeviceAlarmLog l
        where l.source is not null and trim(l.source) <> ''
        order by l.source asc
        """)
    List<String> findDistinctSources();
}
