package com.example.demo.repository;

import com.example.demo.entity.DemoMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoRepo extends JpaRepository<DemoMember, Long>{

}
