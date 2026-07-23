package com.biddrive.core.repositories;
import com.biddrive.core.models.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RideRepository extends JpaRepository<Ride,Integer>{
    
    
}
