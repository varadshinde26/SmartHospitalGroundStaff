# Smart Hospital Ground Staff Management and Workload-Aware Duty Allocation System

A role-based hospital workforce web application for managing ground-level staff, duties, department-scoped supervision, workload-aware allocation and staff-submitted work proof.

## Stack
- JSP + HTML/CSS/JavaScript/AJAX
- Java Servlets
- JDBC
- MySQL
- PHP supporting notification/email service
- Apache Tomcat 9 (`javax.servlet`)

## Core role rules
| Feature | Admin | Supervisor | Staff |
|---|---|---|---|
| Dashboard | Hospital-wide | Assigned departments | Personal |
| Add/manage staff | Yes | No | No |
| View staff | All | Assigned departments only | No |
| Supervisor management | Yes | No | No |
| Create duties | All departments | Assigned departments only | No |
| Smart allocation | All | Assigned departments only | No |
| Leave approval | All | Assigned departments only | No |
| Attendance | All | Assigned departments only | Own |
| Upload work proof | **No** | **No** | **Yes** |
| Review work proof | Yes | Assigned departments only | No |
| Mark work done | Yes | Assigned departments only | No |
| Reports | Hospital-wide | Assigned departments | No |

## Work proof rule
A staff member uploads evidence only against an assigned duty. The file is stored in `documents` with a verification state. Admin/Supervisor can inspect the file and choose **Work done** (VERIFY) or **Reject**. Verification closes the assignment and, when all active assignments are complete, closes the duty.

## Supervisor management
Admin creates supervisor accounts from **Supervisor Management**, assigns one or more departments, and can activate/deactivate/reset password. The mapping is stored in `supervisor_departments`.

## Demo ICU workforce
The bootstrap seeds the demonstration ICU duty with an eligible additional staff member:
- Ramesh Patil — `STF-104` — ICU Attendant — 4.0 years experience
- Skills: Patient Handling, Basic First Aid, ICU Equipment Assistance, Infection Control
- Availability: full-day availability for the next 31 days

For the demo duty **ICU Ground Support Demonstration** (tomorrow, 09:00–13:00), Ramesh is eligible and should appear in Smart Allocation. Because he starts with no assigned workload and has 4 years of experience, the configured score will normally place him ahead of Rahul Patil for this demo duty.

## Smart allocation
Hard eligibility checks happen before scoring:
- active staff
- department match
- role match
- all required skills
- approved leave exclusion
- availability window
- overlapping duty exclusion

Suitability score uses the configured weights:
- Skill Match 30%
- Availability 25%
- Workload 20%
- Experience 15%
- Fairness 10%

## Deployment
See `START_HERE.txt` and `docs/IMPLEMENTATION_MAP.md`.


Ramesh Patil demo staff account:
Username: ramesh101
Password: ramesh@123
Work area: ICU
Role: ICU Attendant
Skills seeded for Smart Allocation: Patient Handling, Basic First Aid, ICU Equipment Assistance, Infection Control.

Staff creation: Admin can optionally create a staff login and must select verified skills in the Add Staff form. Required skills are enforced by Smart Allocation. After a supervisor assigns a duty, the assigned staff dashboard and My Duties page read the assignment directly from duty_assignments.

## Corrected staff management and Smart Allocation
- Administrator has Add/Manage/Edit/Deactivate/Reactivate staff controls.
- Staff management includes verified skills and optional staff login.
- Editing replaces the staff member's skill set instead of leaving stale skills.
- Smart Allocation performs explicit hard eligibility checks for department, role, required verified skills, approved leave, availability, and overlapping duties.
- Allocation errors are returned to the UI instead of silently appearing as an empty recommendation list.
- The ICU demonstration duty is repaired on application startup if an older copy exists.
- Ramesh Patil (STF-104) is seeded as an ICU Attendant with Patient Handling and other ICU skills and a staff login.
