package com.medos.repository;

import com.medos.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByRoomNumber(String roomNumber);
    List<Room> findByWard(String ward);
    List<Room> findByOccupied(Boolean occupied);
    List<Room> findByRoomType(Room.RoomType roomType);
}
