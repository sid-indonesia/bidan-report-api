package org.sidindonesia.bidanreport.repository;

import org.sidindonesia.bidanreport.domain.ChildRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChildRegistrationRepository extends JpaRepository<ChildRegistration, Long> {
}
