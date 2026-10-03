-- Smart Hospital Ground Staff Management
-- COMPLETE FRESH DATABASE / DEMO DATA
-- Compatible with the Java PBKDF2WithHmacSHA256 authentication used by the project.
-- Demo date context: 2026-10-03; demonstration duty: 2026-10-04 09:00-13:00.
-- IMPORTANT: This script DROPS and recreates the smart_hospital database tables.

CREATE DATABASE IF NOT EXISTS smart_hospital CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_hospital;

SET FOREIGN_KEY_CHECKS=0;
DROP TABLE IF EXISTS audit_logs, documents, notifications, duty_acknowledgements,
attendance, workload, leaves, staff_availability, duty_required_skills,
duty_assignments, duties, supervisor_departments, staff_skills, skills,
staff, roles, departments, users;
SET FOREIGN_KEY_CHECKS=1;

CREATE TABLE users (
  user_id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('ADMIN','SUPERVISOR','STAFF') NOT NULL,
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  full_name VARCHAR(120),
  email VARCHAR(150),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE departments (
  department_id INT AUTO_INCREMENT PRIMARY KEY,
  department_name VARCHAR(100) NOT NULL UNIQUE,
  description VARCHAR(255)
);

CREATE TABLE supervisor_departments (
  supervisor_user_id INT NOT NULL,
  department_id INT NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (supervisor_user_id, department_id),
  FOREIGN KEY (supervisor_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  FOREIGN KEY (department_id) REFERENCES departments(department_id) ON DELETE CASCADE
);

CREATE TABLE roles (
  role_id INT AUTO_INCREMENT PRIMARY KEY,
  role_name VARCHAR(100) NOT NULL UNIQUE,
  description VARCHAR(255)
);

CREATE TABLE staff (
  staff_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT UNIQUE,
  staff_code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  phone VARCHAR(20),
  email VARCHAR(150),
  department_id INT NOT NULL,
  role_id INT NOT NULL,
  experience_years DECIMAL(4,1) DEFAULT 0,
  profile_image VARCHAR(255),
  status ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE',
  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
  FOREIGN KEY (department_id) REFERENCES departments(department_id),
  FOREIGN KEY (role_id) REFERENCES roles(role_id)
);

CREATE TABLE skills (
  skill_id INT AUTO_INCREMENT PRIMARY KEY,
  skill_name VARCHAR(100) NOT NULL UNIQUE,
  description VARCHAR(255)
);

CREATE TABLE staff_skills (
  staff_id INT NOT NULL,
  skill_id INT NOT NULL,
  verification_status ENUM('VERIFIED','PENDING') DEFAULT 'VERIFIED',
  PRIMARY KEY (staff_id, skill_id),
  FOREIGN KEY (staff_id) REFERENCES staff(staff_id) ON DELETE CASCADE,
  FOREIGN KEY (skill_id) REFERENCES skills(skill_id) ON DELETE CASCADE
);

CREATE TABLE duties (
  duty_id INT AUTO_INCREMENT PRIMARY KEY,
  duty_name VARCHAR(150) NOT NULL,
  department_id INT NOT NULL,
  role_id INT NOT NULL,
  duty_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  priority ENUM('LOW','NORMAL','HIGH','EMERGENCY') DEFAULT 'NORMAL',
  status ENUM('OPEN','ASSIGNED','IN_PROGRESS','COMPLETED','CANCELLED') DEFAULT 'OPEN',
  description TEXT,
  created_by INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (department_id) REFERENCES departments(department_id),
  FOREIGN KEY (role_id) REFERENCES roles(role_id),
  FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE duty_required_skills (
  duty_id INT NOT NULL,
  skill_id INT NOT NULL,
  PRIMARY KEY (duty_id, skill_id),
  FOREIGN KEY (duty_id) REFERENCES duties(duty_id) ON DELETE CASCADE,
  FOREIGN KEY (skill_id) REFERENCES skills(skill_id) ON DELETE CASCADE
);

CREATE TABLE duty_assignments (
  assignment_id INT AUTO_INCREMENT PRIMARY KEY,
  duty_id INT NOT NULL,
  staff_id INT NOT NULL,
  assigned_by INT NOT NULL,
  assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  status ENUM('ASSIGNED','ACKNOWLEDGED','IN_PROGRESS','COMPLETED','CANCELLED','CHANGE_REQUESTED') DEFAULT 'ASSIGNED',
  UNIQUE KEY uq_duty_staff(duty_id, staff_id),
  FOREIGN KEY (duty_id) REFERENCES duties(duty_id) ON DELETE CASCADE,
  FOREIGN KEY (staff_id) REFERENCES staff(staff_id),
  FOREIGN KEY (assigned_by) REFERENCES users(user_id)
);

CREATE TABLE staff_availability (
  availability_id INT AUTO_INCREMENT PRIMARY KEY,
  staff_id INT NOT NULL,
  available_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  status ENUM('AVAILABLE','UNAVAILABLE') DEFAULT 'AVAILABLE',
  FOREIGN KEY (staff_id) REFERENCES staff(staff_id) ON DELETE CASCADE
);

CREATE TABLE leaves (
  leave_id INT AUTO_INCREMENT PRIMARY KEY,
  staff_id INT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING',
  approved_by INT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (staff_id) REFERENCES staff(staff_id),
  FOREIGN KEY (approved_by) REFERENCES users(user_id)
);

CREATE TABLE attendance (
  attendance_id INT AUTO_INCREMENT PRIMARY KEY,
  staff_id INT NOT NULL,
  duty_id INT,
  attendance_date DATE NOT NULL,
  check_in DATETIME,
  check_out DATETIME,
  status ENUM('PRESENT','ABSENT','LATE','PARTIAL') DEFAULT 'PRESENT',
  FOREIGN KEY (staff_id) REFERENCES staff(staff_id),
  FOREIGN KEY (duty_id) REFERENCES duties(duty_id) ON DELETE SET NULL
);

CREATE TABLE workload (
  workload_id INT AUTO_INCREMENT PRIMARY KEY,
  staff_id INT NOT NULL,
  workload_date DATE NOT NULL,
  duty_hours DECIMAL(6,2) DEFAULT 0,
  duty_count INT DEFAULT 0,
  workload_level ENUM('LOW','MODERATE','HIGH','OVERLOAD') DEFAULT 'LOW',
  UNIQUE KEY uq_staff_day(staff_id,workload_date),
  FOREIGN KEY (staff_id) REFERENCES staff(staff_id) ON DELETE CASCADE
);

CREATE TABLE notifications (
  notification_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  message VARCHAR(500) NOT NULL,
  type VARCHAR(40) NOT NULL,
  status ENUM('UNREAD','READ') DEFAULT 'UNREAD',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE duty_acknowledgements (
  acknowledgement_id INT AUTO_INCREMENT PRIMARY KEY,
  assignment_id INT NOT NULL,
  staff_id INT NOT NULL,
  status ENUM('ACKNOWLEDGED','DECLINED','CHANGE_REQUESTED') NOT NULL,
  acknowledged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (assignment_id) REFERENCES duty_assignments(assignment_id) ON DELETE CASCADE,
  FOREIGN KEY (staff_id) REFERENCES staff(staff_id)
);

CREATE TABLE documents (
  document_id INT AUTO_INCREMENT PRIMARY KEY,
  staff_id INT NOT NULL,
  duty_id INT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  file_type VARCHAR(100) NOT NULL,
  verification_status ENUM('PENDING','VERIFIED','REJECTED') NOT NULL DEFAULT 'PENDING',
  reviewed_by INT NULL,
  reviewed_at DATETIME NULL,
  review_note VARCHAR(500),
  uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (staff_id) REFERENCES staff(staff_id) ON DELETE CASCADE,
  FOREIGN KEY (duty_id) REFERENCES duties(duty_id) ON DELETE SET NULL,
  FOREIGN KEY (reviewed_by) REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE TABLE audit_logs (
  log_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT,
  action VARCHAR(100) NOT NULL,
  description VARCHAR(500) NOT NULL,
  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL
);

INSERT INTO departments (department_id, department_name, description) VALUES
(1,'ICU','Intensive care ground support'),
(2,'Emergency/Casualty','Emergency ground operations'),
(3,'General Ward','General ward support'),
(4,'Housekeeping','Cleaning and sanitation'),
(5,'Operation Theatre','OT support services'),
(6,'Laboratory','Laboratory support'),
(7,'Maintenance','Technical maintenance'),
(8,'Patient Transport','Patient movement and transport');

INSERT INTO roles (role_id, role_name, description) VALUES
(1,'ICU Attendant','Ground support for ICU'),
(2,'Emergency Attendant','Emergency area ground support'),
(3,'Ward Attendant','Ward operations'),
(4,'Cleaner','Housekeeping and sanitation'),
(5,'OT Assistant','Operation theatre support'),
(6,'Lab Assistant','Laboratory support'),
(7,'Maintenance Technician','Electrical/plumbing/equipment support'),
(8,'Patient Transporter','Patient transport and wheelchair support');

INSERT INTO skills (skill_id, skill_name, description) VALUES
(1,'Patient Handling','Safe patient movement and handling'),
(2,'Basic First Aid','Basic first-aid response'),
(3,'ICU Equipment Assistance','Support for ICU equipment'),
(4,'Infection Control','Hospital infection-control practices'),
(5,'Emergency Response','Emergency response support'),
(6,'Cleaning & Sanitation','Cleaning and sanitation procedures'),
(7,'OT Protocol','Operation theatre protocols'),
(8,'Specimen Handling','Laboratory specimen handling'),
(9,'Electrical Maintenance','Electrical maintenance'),
(10,'Plumbing','Plumbing maintenance'),
(11,'Equipment Maintenance','General equipment maintenance'),
(12,'Wheelchair Handling','Wheelchair and stretcher handling');

INSERT INTO users
(user_id, username, password_hash, role, status, full_name, email)
VALUES
(1,'admin','PBKDF2$120000$Fama6LkWB2mjcYPBXVwK2A==$Gebpb6tIrATbSyixYO5M4vl5KDYWz4xcpDokwUnj+wc=','ADMIN','ACTIVE','System Administrator','admin@hospital.local'),
(2,'supervisor','PBKDF2$120000$3zKN+MJK7eSpOlyXBMWAQw==$ivUzj/nNQpEmZCsL4yTMkPuFIBWCdKZGnyyH+jis5GQ=','SUPERVISOR','ACTIVE','ICU Supervisor','supervisor@hospital.local'),
(3,'staff101','PBKDF2$120000$3mvcNUsmsbGYeUrW2ynUtg==$GW5CCwRyDzaGkG2vnOtF5dZKUtmvMZ9KRMT+KGWtlZE=','STAFF','ACTIVE','Rahul Patil','rahul@example.com'),
(4,'ramesh101','PBKDF2$120000$INg11062X3ew2RffCXNylw==$xisSoMUFj9/p8QKfsAJp6QgZG35EUj3mmwZ7cBQSD24=','STAFF','ACTIVE','Ramesh Patil','ramesh@example.com');

INSERT INTO supervisor_departments(supervisor_user_id, department_id, active)
VALUES (2,1,1);

INSERT INTO staff
(staff_id,user_id,staff_code,name,phone,email,department_id,role_id,experience_years,status)
VALUES
(1,3,'STF-101','Rahul Patil','9876543210','rahul@example.com',1,1,3.0,'ACTIVE'),
(2,NULL,'STF-102','Neha Kulkarni','9876543211','neha@example.com',3,3,5.0,'ACTIVE'),
(3,NULL,'STF-103','Amit Joshi','9876543212','amit@example.com',2,2,2.0,'ACTIVE'),
(4,4,'STF-104','Ramesh Patil','9876543213','ramesh@example.com',1,1,4.0,'ACTIVE');

INSERT INTO staff_skills(staff_id,skill_id,verification_status) VALUES
(1,1,'VERIFIED'),(1,2,'VERIFIED'),(1,3,'VERIFIED'),(1,4,'VERIFIED'),
(2,1,'VERIFIED'),(2,2,'VERIFIED'),(2,4,'VERIFIED'),
(3,1,'VERIFIED'),(3,2,'VERIFIED'),(3,5,'VERIFIED'),
(4,1,'VERIFIED'),(4,2,'VERIFIED'),(4,3,'VERIFIED'),(4,4,'VERIFIED');

INSERT INTO staff_availability(staff_id,available_date,start_time,end_time,status)
SELECT s.staff_id, d.available_date, '00:00:00','23:59:59','AVAILABLE'
FROM staff s
JOIN (
  SELECT DATE_ADD('2026-10-03', INTERVAL n DAY) AS available_date
  FROM (
    SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
    UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11
    UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
    UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23
    UNION ALL SELECT 24 UNION ALL SELECT 25 UNION ALL SELECT 26 UNION ALL SELECT 27
    UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30
  ) x
) d
WHERE s.status='ACTIVE';

INSERT INTO duties
(duty_id,duty_name,department_id,role_id,duty_date,start_time,end_time,
 priority,status,description,created_by)
VALUES
(1,'ICU Ground Support Demonstration',1,1,'2026-10-04','09:00:00','13:00:00',
 'HIGH','OPEN',
 'Demo duty for testing eligibility, workload-aware recommendations and assignment workflow.',
 2);

INSERT INTO duty_required_skills(duty_id,skill_id)
VALUES (1,1);

INSERT INTO workload(staff_id,workload_date,duty_hours,duty_count,workload_level)
SELECT staff_id,'2026-10-04',0,0,'LOW'
FROM staff
WHERE status='ACTIVE';

-- Verification:
-- SELECT user_id,username,role,status,full_name,email FROM users;
-- SELECT s.staff_id,s.staff_code,s.name,d.department_name,r.role_name,s.experience_years,s.status
-- FROM staff s JOIN departments d ON s.department_id=d.department_id JOIN roles r ON s.role_id=r.role_id;
-- SELECT u.username,d.department_name FROM supervisor_departments sd
-- JOIN users u ON u.user_id=sd.supervisor_user_id
-- JOIN departments d ON d.department_id=sd.department_id;
-- SELECT duty_id,duty_name,duty_date,start_time,end_time,priority,status FROM duties;
