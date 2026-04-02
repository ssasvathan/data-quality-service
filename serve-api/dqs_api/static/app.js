document.addEventListener("DOMContentLoaded", () => {
    fetch("/api/datasets")
        .then(r => r.json())
        .then(data => {
            const ul = document.getElementById("dataset-list");
            data.forEach(ds => {
                const li = document.createElement("li");
                li.textContent = ds.src_sys_nm;
                ul.appendChild(li);
            });
        });
});
