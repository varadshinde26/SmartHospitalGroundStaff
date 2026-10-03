# PHP supporting service

Place this folder under XAMPP `htdocs` as `smart-hospital-php`.

Endpoints:
- `health.php` — service health check.
- `notify.php` — supporting email notification service used by the Java application after a duty assignment.
- `upload.php` — validated file/document upload endpoint for JPG/JPEG/PNG/PDF files up to 5 MB.

Configure PHP's `mail()` or your local SMTP relay for actual email delivery. The core Java application does not fail if the PHP mail service is temporarily unavailable.
