package com.example.smsbackend.repository;

import com.example.smsbackend.entity.Device;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    List<Device> findByUserIdOrderByNameAsc(Long userId);

    long countByUserLocationId(Long locationId);

    List<Device> findByUserLocationId(Long locationId);

    long countByUserCompanyId(Long companyId);

    Optional<Device> findByExternalDeviceId(String externalDeviceId);

    Optional<Device> findByPhoneNumber(String phoneNumber);

    @Query("""
        select distinct d.alarmCode
        from Device d
        where d.alarmCode is not null and trim(d.alarmCode) <> ''
        order by d.alarmCode asc
        """)
    List<String> findDistinctActiveAlarmCodes();
}
