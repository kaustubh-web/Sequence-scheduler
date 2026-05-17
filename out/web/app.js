const tasks = [];

const elements = {
    taskForm: document.getElementById("taskForm"),
    taskId: document.getElementById("taskId"),
    taskName: document.getElementById("taskName"),
    taskDuration: document.getElementById("taskDuration"),
    taskDependencies: document.getElementById("taskDependencies"),
    projectStartDate: document.getElementById("projectStartDate"),
    taskList: document.getElementById("taskList"),
    generateButton: document.getElementById("generateButton"),
    loadSampleButton: document.getElementById("loadSampleButton"),
    results: document.getElementById("results"),
    message: document.getElementById("message"),
    taskCount: document.getElementById("taskCount"),
    durationCount: document.getElementById("durationCount")
};

elements.projectStartDate.value = new Date().toISOString().slice(0, 10);

elements.taskForm.addEventListener("submit", (event) => {
    event.preventDefault();

    const id = elements.taskId.value.trim();
    const name = elements.taskName.value.trim();
    const durationDays = Number(elements.taskDuration.value);
    const dependencies = Array.from(elements.taskDependencies.selectedOptions).map((option) => option.value);

    if (!id || !name || durationDays <= 0) {
        setMessage("Each task needs an id, name, and duration.");
        return;
    }

    if (tasks.some((task) => task.id === id)) {
        setMessage(`Task id "${id}" already exists.`);
        return;
    }

    tasks.push({ id, name, durationDays, dependencies });
    elements.taskForm.reset();
    elements.taskDuration.value = "1";
    setMessage(`Task ${id} added.`);
    syncTaskViews();
});

elements.generateButton.addEventListener("click", async () => {
    if (!tasks.length) {
        setMessage("Add at least one task first.");
        return;
    }

    try {
        const response = await fetch("/api/schedule", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                projectStartDate: elements.projectStartDate.value,
                tasks
            })
        });

        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error || "Could not generate schedule.");
        }

        renderSchedule(data);
        setMessage("Schedule generated.");
    } catch (error) {
        setMessage(error.message);
    }
});

elements.loadSampleButton.addEventListener("click", () => {
    tasks.length = 0;
    tasks.push(
        { id: "T1", name: "Requirement Analysis", durationDays: 2, dependencies: [] },
        { id: "T2", name: "UI Design", durationDays: 3, dependencies: ["T1"] },
        { id: "T3", name: "Backend Logic", durationDays: 4, dependencies: ["T1"] },
        { id: "T4", name: "Integration Testing", durationDays: 2, dependencies: ["T2", "T3"] }
    );
    syncTaskViews();
    setMessage("Sample project loaded.");
});

function syncTaskViews() {
    renderDependencyOptions();
    renderTaskList();
    elements.taskCount.textContent = String(tasks.length);
}

function renderDependencyOptions() {
    elements.taskDependencies.innerHTML = "";
    for (const task of tasks) {
        const option = document.createElement("option");
        option.value = task.id;
        option.textContent = `${task.id} - ${task.name}`;
        elements.taskDependencies.appendChild(option);
    }
}

function renderTaskList() {
    if (!tasks.length) {
        elements.taskList.className = "task-list empty";
        elements.taskList.textContent = "No tasks added yet.";
        elements.durationCount.textContent = "0";
        return;
    }

    elements.taskList.className = "task-list";
    elements.taskList.innerHTML = tasks.map((task) => `
        <article class="task-card">
            <h3>${escapeHtml(task.name)}</h3>
            <div class="meta">
                <span class="pill">${escapeHtml(task.id)}</span>
                <span class="pill">${task.durationDays} day(s)</span>
                <span class="pill">Depends on: ${task.dependencies.length ? escapeHtml(task.dependencies.join(", ")) : "None"}</span>
            </div>
        </article>
    `).join("");
}

function renderSchedule(schedule) {
    const total = schedule.totalDurationDays || 1;
    elements.durationCount.textContent = String(schedule.totalDurationDays);

    elements.results.className = "results";
    elements.results.innerHTML = schedule.tasks.map((task) => {
        const width = Math.max((task.durationDays / total) * 100, 6);
        const startDate = addDays(schedule.projectStartDate, task.startDay);
        const finishDate = addDays(schedule.projectStartDate, task.finishDay);

        return `
            <article class="schedule-card">
                <h3>${escapeHtml(task.id)} - ${escapeHtml(task.name)}</h3>
                <div class="meta">
                    <span class="pill">Start day: ${task.startDay}</span>
                    <span class="pill">Finish day: ${task.finishDay}</span>
                    <span class="pill">${escapeHtml(startDate)} to ${escapeHtml(finishDate)}</span>
                </div>
                <div class="timeline">
                    <div class="timeline-fill" style="width:${width}%"></div>
                </div>
            </article>
        `;
    }).join("");
}

function addDays(dateText, days) {
    const date = new Date(`${dateText}T00:00:00`);
    date.setDate(date.getDate() + days);
    return date.toISOString().slice(0, 10);
}

function setMessage(text) {
    elements.message.textContent = text;
}

function escapeHtml(value) {
    return value
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}

syncTaskViews();
