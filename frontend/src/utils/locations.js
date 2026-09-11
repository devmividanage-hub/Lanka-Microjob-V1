/** Sri Lankan district -> city reference data used by every registration and job form. */

export const districtCities = {
    'Colombo': ['Colombo City', 'Dehiwala', 'Moratuwa', 'Kotte', 'Nugegoda', 'Maharagama', 'Kaduwela', 'Homagama', 'Kesbewa', 'Kolonnawa', 'Ratmalana', 'Boralesgamuwa', 'Hanwella'],
    'Gampaha': ['Negombo', 'Gampaha', 'Ja-Ela', 'Wattala', 'Minuwangoda', 'Katunayake', 'Kadawatha', 'Veyangoda', 'Mirigama', 'Nittambuwa', 'Ragama', 'Kiribathgoda', 'Ekala'],
    'Kalutara': ['Kalutara', 'Panadura', 'Horana', 'Beruwala', 'Aluthgama', 'Bandaragama', 'Ingiriya', 'Matugama', 'Wadduwa', 'Payagala'],
    'Kandy': ['Kandy City', 'Peradeniya', 'Katugastota', 'Gampola', 'Nawalapitiya', 'Teldeniya', 'Kundasale', 'Digana', 'Kadugannawa', 'Pilimathalawa'],
    'Matale': ['Matale', 'Dambulla', 'Sigiriya', 'Galewela', 'Rattota', 'Ukuwela', 'Pallepola'],
    'Nuwara Eliya': ['Nuwara Eliya', 'Hatton', 'Talawakelle', 'Ginigathena', 'Ragala', 'Walapane', 'Kotagala'],
    'Galle': ['Galle', 'Hikkaduwa', 'Ambalangoda', 'Elpitiya', 'Baddegama', 'Karandeniya', 'Bentota', 'Balapitiya', 'Ahangama'],
    'Matara': ['Matara', 'Weligama', 'Akuressa', 'Devinuwara', 'Hakmana', 'Kamburupitiya', 'Dikwella'],
    'Hambantota': ['Hambantota', 'Tangalle', 'Tissamaharama', 'Ambalantota', 'Beliatta', 'Weeraketiya', 'Suriyawewa'],
    'Jaffna': ['Jaffna', 'Chavakachcheri', 'Point Pedro', 'Nallur', 'Manipay', 'Kopay'],
    'Kilinochchi': ['Kilinochchi', 'Pallai', 'Paranthan', 'Poonakary'],
    'Mannar': ['Mannar', 'Nanattan', 'Murunkan', 'Madhu'],
    'Vavuniya': ['Vavuniya', 'Cheddikulam', 'Nedunkeni', 'Omanthai'],
    'Mullaitivu': ['Mullaitivu', 'Puthukkudiyiruppu', 'Oddusuddan'],
    'Batticaloa': ['Batticaloa', 'Kattankudy', 'Valaichhenai', 'Eravur', 'Kalmunai'],
    'Ampara': ['Ampara', 'Kalmunai', 'Akkaraipattu', 'Pottuvil', 'Sammanthurai', 'Sainthamaruthu'],
    'Trincomalee': ['Trincomalee', 'Kinniya', 'Mutur', 'Kantale', 'Thampalakamam'],
    'Kurunegala': ['Kurunegala', 'Kuliyapitiya', 'Narammala', 'Pannala', 'Giriulla', 'Hettipola', 'Mawathagama', 'Ibbagamuwa'],
    'Puttalam': ['Puttalam', 'Chilaw', 'Wennappuwa', 'Marawila', 'Dankotuwa', 'Nattandiya', 'Anamaduwa'],
    'Anuradhapura': ['Anuradhapura', 'Medawachchiya', 'Kekirawa', 'Eppawala', 'Tambuttegama', 'Mihintale', 'Nochchiyagama'],
    'Polonnaruwa': ['Polonnaruwa', 'Kaduruwela', 'Medirigiriya', 'Hingurakgoda', 'Minneriya'],
    'Badulla': ['Badulla', 'Bandarawela', 'Welimada', 'Ella', 'Haputale', 'Mahiyanganaya', 'Passara'],
    'Monaragala': ['Monaragala', 'Wellawaya', 'Bibile', 'Medagama', 'Buttala', 'Kataragama'],
    'Ratnapura': ['Ratnapura', 'Embilipitiya', 'Balangoda', 'Pelmadulla', 'Eheliyagoda', 'Kuruwita'],
    'Kegalle': ['Kegalle', 'Mawanella', 'Warakapola', 'Rambukkana', 'Dehiovita', 'Ruwanwella'],
};

export const districts = () => Object.keys(districtCities).sort();

export const citiesOf = (district) => districtCities[district] || [];

/** Fills every district <select> listed in `ids` with the full district list. */
export function populateDistrictSelects(ids) {
    ids.forEach(id => {
        const select = document.getElementById(id);
        if (!select) return;
        const placeholder = select.options[0] ? select.options[0].outerHTML : '<option value="">Select District...</option>';
        select.innerHTML = placeholder + districts()
            .map(district => `<option value="${district}">${district}</option>`)
            .join('');
    });
}

/** District -> city cascade used by the worker, employer, broker and job forms. */
export function cascadeCity(districtId, cityFieldId, cityId, onChange) {
    const districtEl = document.getElementById(districtId);
    const cityField = document.getElementById(cityFieldId);
    const cityEl = document.getElementById(cityId);
    if (!districtEl || !cityEl) return;
    const district = districtEl.value;
    const cities = citiesOf(district);
    cityEl.innerHTML = '<option value="">Select City...</option>' + cities
        .map(city => `<option value="${city}">${city}</option>`)
        .join('');
    if (cityField) cityField.style.display = cities.length ? 'block' : 'none';
    if (typeof onChange === 'function') onChange(district, cityEl.value);
}
