package org.sidindonesia.bidanreport.repository;

import org.sidindonesia.bidanreport.domain.MotherEdit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MotherEditRepository extends JpaRepository<MotherEdit, Long> {
}
