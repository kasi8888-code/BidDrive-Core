package com.biddrive.core.repositories;

import com.biddrive.core.models.DriverWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DriverWalletRepository extends JpaRepository<DriverWallet, Integer> {

    Optional<DriverWallet> findByDriverId(Integer driverId);
}
