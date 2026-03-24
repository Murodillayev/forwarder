// Alert xabarlarni 5 soniyadan keyin yashirish
document.addEventListener('DOMContentLoaded', function () {
    const alerts = document.querySelectorAll('.alert.alert-success, .alert.alert-danger');
    alerts.forEach(function (alert) {
        setTimeout(function () {
            const bsAlert = bootstrap.Alert.getOrCreateInstance(alert);
            bsAlert.close();
        }, 5000);
    });

    // Pattern radio tugmalarini bosish orqali kartani belgilash
    document.querySelectorAll('.pattern-option input[type=radio]').forEach(function (radio) {
        // Sahifa yuklanganda ham ishlashi uchun
        if (radio.checked) {
            radio.closest('.pattern-option').classList.add('border-dark', 'bg-dark', 'bg-opacity-10');
        }

        radio.addEventListener('change', function () {
            // Barchasidan olib tashlash
            document.querySelectorAll('.pattern-option').forEach(function (el) {
                el.classList.remove('border-dark', 'bg-dark', 'bg-opacity-10');
            });
            // Tanlanganiga qo'shish
            this.closest('.pattern-option').classList.add('border-dark', 'bg-dark', 'bg-opacity-10');
        });
    });

    // Kanal inputidan @ belgisini avtomatik olib tashlash
    document.querySelectorAll('input[name="sourceChannel"], input[name="targetChannel"]').forEach(function (input) {
        input.addEventListener('blur', function () {
            this.value = this.value.replace(/^@/, '').trim();
        });
    });
});
